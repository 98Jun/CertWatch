package com.certwatch.service.impl;

import cc.maria.rdap.RDAPClient;
import cc.maria.rdap.object.DomainObjectClass;
import cc.maria.rdap.object.Event;
import cc.maria.rdap.object.ObjectReference;
import cc.maria.rdap.object.ObjectType;
import com.certwatch.config.CertwatchProperties;
import com.certwatch.entity.CheckDTO;
import com.certwatch.service.DomainCheckerService;
import com.common.service.CommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service("DoaminCheckerService")
public class DomainCheckerServiceImpl implements DomainCheckerService {
    /** 구성 속성 주입 (application.properties 바인딩된 값) */

    @Autowired
    private CommonService commonService;

    @Autowired
    private CertwatchProperties props;

    @Override
    public List<CheckDTO> checkAllAndMaybeNotify() {
        // 1) 타깃 목록을 로드
        List<String> targets = commonService.loadTargets(props);

        // 2) 병렬로 점검 실행
        List<CheckDTO> results = runCheck(targets, props.getTimeoutSeconds(), props.getWorkers());
        // 3) 결과 정렬 (성공 건은 daysLeft 오름차순, 실패 건은 마지막에)
        results.sort(Comparator
                .comparing((CheckDTO r) -> r.ok ? r.daysLeft : Long.MAX_VALUE)
                .thenComparing(r -> r.host));

        // 4) 텔레그램 전송 (토큰/챗ID가 모두 존재할 때만)
        if (props.getTelegram() != null &&
                commonService.stringNullCheck(props.getTelegram().getToken()) && commonService.stringNullCheck(props.getTelegram().getChatId())) {

            HttpClient client = HttpClient.newHttpClient(); // JDK 11 HttpClient

            //메세지 작성
            for (CheckDTO r : results) {
                // 알림 대상: 점검 실패 또는 남은 일수 <= 임계치
                boolean shouldAlert = !r.ok || (r.ok && r.daysLeft <= props.getThresholdDays());
                if (shouldAlert) {

                    try {
                        // 실제 메시지 전송
                        commonService.sendTelegram(client,
                                props.getTelegram().getToken(),
                                props.getTelegram().getChatId(),
                                commonService.formatTelegram(r));
                        // 너무 빠른 연속 전송 방지(간단한 rate-limit)
                        Thread.sleep(200);
                    } catch (Exception ignored) {

                    }
                }
            }
        }
        return results;
    }

    /**
     * 설정으로부터 타깃 목록을 읽어옵니다.
     * - certwatch.targets : 쉼표 목록
     * - certwatch.targets-file : 라인 파일 (상대/절대 경로 모두 허용)
     */

    /**
     * 주어진 타깃 목록을 스레드 풀로 병렬 점검합니다.
     */
    public List<CheckDTO> runCheck(List<String> targets, int timeoutSeconds, int workers) {
        if (targets == null) targets = List.of();                 // null 방어

        // 1) 고정 스레드풀 생성 (최소 1개)
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, workers));

        // 2) 제출할 작업 목록 생성
        List<Future<CheckDTO>> futures = new ArrayList<>();
        for (String t : targets) {
            String[] hp = commonService.parseTarget(t);                         // "host:port" 파싱
            if (hp == null) continue;                             // 불량 라인은 건너뜀
            String host = hp[0];
            int port = Integer.parseInt(hp[1]);
            // 각 호스트별 점검을 Callable로 제출
            futures.add(pool.submit(() -> checkOne(host, port, timeoutSeconds)));
        }

        // 3) 완료된 작업에서 결과 수집
        List<CheckDTO> out = new ArrayList<>();
        for (Future<CheckDTO> f : futures) {
            try {
                out.add(f.get());                                 // (예외는 개별 무시)
            } catch (Exception ignored) {}
        }

        // 4) 스레드풀 종료
        pool.shutdown();

        // 5) 결과 반환
        return out;
    }

    private static CheckDTO checkOne(String host, int port, int timeoutSec){

        CheckDTO dto = new CheckDTO();

        dto.setHost(host);
        dto.setPort(port);
        dto.setType("Domain");

        try{

            String domainName = dto.getHost();

            RDAPClient client = new RDAPClient();
            DomainObjectClass domain = client.queryDomain(
                    new ObjectReference(domainName, ObjectType.DOMAIN)
            );

            Optional<Event> expiration = Arrays.stream(domain.getEvents())
                    .filter(e -> "expiration".equalsIgnoreCase(e.getEventAction()))
                    .findFirst();

            if (expiration.isEmpty()) {
                dto.setError("⚠️ 만료일 이벤트를 찾을 수 없습니다.");
                dto.setOk(false);
            }

            String raw = expiration.get().getEventDate();

//            System.out.println("원본(RDAP raw): " + raw);

            Instant expInstant = tryParseRdapDate(raw);
            if (expInstant != null) {

                dto.setNotAfter(expInstant);

                ZonedDateTime expKST = expInstant.atZone(ZoneId.of("Asia/Seoul"));
                ZonedDateTime nowKST = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
                long daysLeft = Duration.between(nowKST, expKST).toDays();

                dto.setDaysLeft(daysLeft);
//                System.out.println("만료일 (KST): " + expKST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")));
//                System.out.println("남은 일수: " + daysLeft + "일");

                dto.setOk(true);
            } else {
                dto.setOk(false);
                dto.setError("❌ 날짜 파싱 실패");
            }
        }catch (Exception e){
            dto.setOk(false);
            dto.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return dto;
    }

    private static Instant tryParseRdapDate(String s) {
        Instant result = null;
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                if (p.contains("XXX")) {

                    return OffsetDateTime.parse(s, DateTimeFormatter.ofPattern(p)).toInstant();
                } else if (p.endsWith("'Z'")) {
                    return Instant.from(DateTimeFormatter.ofPattern(p).withZone(ZoneOffset.UTC).parse(s));
                } else {
                    return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)).atStartOfDay(ZoneOffset.UTC).toInstant();
                }
            } catch (Exception ignored) {

            }
        }
        return result;
    }
}
