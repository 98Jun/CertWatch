package com.certwatch.service.impl;

import com.certwatch.config.CertwatchProperties;
import com.certwatch.entity.CheckDTO;
import com.certwatch.service.CertCheckerService;
import com.common.service.CommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service("CertCheckerService")
public class CertCheckerServiceImpl implements CertCheckerService {

    /** 구성 속성 주입 (application.properties 바인딩된 값) */
    @Autowired
    private CertwatchProperties props;

    @Autowired
    private CommonService commonService;

    /**
     * 모든 타깃을 점검하고, 임계치 이하/오류는 텔레그램으로 전송합니다.
     * @return 정렬된 결과 리스트 (만료 임박 순)
     */
    public List<CheckDTO> checkAllAndMaybeNotify() {
        // 1) 타깃 목록을 로드
        List<String> targets = loadTargets();

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
                //임시 추가
                r.setType("SSL");

                // 알림 대상: 점검 실패 또는 남은 일수 <= 임계치
                boolean shouldAlert = !r.ok || (r.ok && r.daysLeft <= props.getThresholdDays());
                if (shouldAlert) {

                    try {
                        // 실제 메시지 전송
                        sendTelegram(client,
                                props.getTelegram().getToken(),
                                props.getTelegram().getChatId(),
                                formatTelegram(r));
                        // 너무 빠른 연속 전송 방지(간단한 rate-limit)
                        Thread.sleep(200);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 5) 컨트롤러에서 바로 반환할 수 있게 결과 리턴
        return results;
    }
    /**
     * 설정으로부터 타깃 목록을 읽어옵니다.
     * - certwatch.targets : 쉼표 목록
     * - certwatch.targets-file : 라인 파일 (상대/절대 경로 모두 허용)
     */
    public List<String> loadTargets() {
        // 결과를 누적할 리스트 생성
        List<String> list = new ArrayList<>();

        // 1) 프로퍼티의 쉼표 목록 추가
        if (props.getTargets() != null) {
            for (String s : props.getTargets()) {
                if (s == null) continue;          // null 방어
                String t = s.trim();               // 앞뒤 공백 제거
                if (!t.isEmpty()) list.add(t);     // 빈 문자열이 아니면 추가
            }
        }

        // 2) 파일 경로가 지정되었으면 파일에서 한 줄씩 읽어 추가
        if (props.getTargetsFile() != null && !props.getTargetsFile().isBlank()) {
            try (BufferedReader br = new BufferedReader(new FileReader(props.getTargetsFile()))) {
                String line;
                while ((line = br.readLine()) != null) {   // EOF까지 라인 반복
                    String t = line.trim();                // 공백 제거
                    if (t.isEmpty() || t.startsWith("#"))  // 빈 줄/주석(#) 무시
                        continue;
                    list.add(t);                           // 유효 라인이면 추가
                }
            } catch (Exception e) {
            }
        }

        // 누적된 목록 반환
        return list;
    }

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
            String[] hp = parseTarget(t);                         // "host:port" 파싱
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

    /** "host[:port]" 문자열을 [host, port] 배열로 파싱 (포트 없으면 443) */
    private static String[] parseTarget(String line) {
        String s = line.trim();                                   // 공백 제거
        if (s.isEmpty() || s.startsWith("#")) return null;        // 빈 줄/주석 무시
        if (s.contains(":")) {                                    // 포트 표기 있는 경우
            String[] parts = s.split(":", 2);                     // 앞에서 1번만 분리
            try {
                Integer.parseInt(parts[1]);                       // 포트 유효성 검사
                return new String[]{parts[0].trim(), parts[1].trim()};
            } catch (NumberFormatException e) {
                return null;                                      // 잘못된 포트면 무시
            }
        } else {
            return new String[]{s, "443"};                        // 기본 포트 443
        }
    }

    /** 텔레그램으로 간단한 텍스트 메시지를 전송합니다. */
    private static void sendTelegram(HttpClient client, String token, String chatId, String text) throws Exception {
        // URL-encoded form 바디 구성
        String body = "chat_id=" + java.net.URLEncoder.encode(chatId, java.nio.charset.StandardCharsets.UTF_8)
                + "&text=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8)
                + "&parse_mode=HTML&disable_web_page_preview=true";

        // HTTP POST 요청 구성
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // 요청 전송 (응답 바디는 사용하지 않으므로 버림)
        client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** 알림 메시지 포맷 (HTML 파싱 모드) */
    private static String formatTelegram(CheckDTO r) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));    // ISO-8601 포맷

        String result = "";
        if (r.ok) {
            result = "🔔 <b>"+r.type+" 만료 임박</b>\n"
                    + "• 대상: <code>" + r.host + "</code>\n"
                    + "• 남은 일수: <b>" + r.daysLeft + "일</b>\n"
                    + "• 만료일(한국시간): <code>" + fmt.format(r.notAfter) + "</code>";
        }  else {
            return "⚠️ <b>SSL 확인 실패</b>\n"
                    + "• 대상: <code>" + r.host + "</code>\n"
                    + "• 오류: <code>" + r.error + "</code>";
        }
        return result;
    }

    /**
     * 단일 호스트의 인증서 만료 정보를 확인합니다.
     * - TLS 연결을 맺되, 신뢰 검증은 끄고(not verifying) "만료일"만 읽습니다.
     * - SNI(Server Name Indication)를 설정하여 가상호스팅에서도 올바른 인증서를 받습니다.
     */
    private static CheckDTO checkOne(String host, int port, int timeoutSec) {
        long start = System.nanoTime();                            // 성능 측정 시작
        CheckDTO r = new CheckDTO();                         // 결과 객체 생성
        r.host = host;                                             // 호스트 세팅
        r.port = port;                                             // 포트 세팅
        try {
            // 1) TLS 컨텍스트 생성
            SSLContext ctx = SSLContext.getInstance("TLS");

            // 2) 모든 서버 인증서를 "신뢰"하도록 커스텀 TrustManager 구성
            //    (만료/자체서명/호스트명 불일치라도 만료일 읽기를 위해)
            ctx.init(null, new TrustManager[]{ new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] xcs, String s) {}
                public void checkServerTrusted(X509Certificate[] xcs, String s) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());

            // 3) SSL 소켓 팩토리에서 소켓 생성
            SSLSocketFactory factory = ctx.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket()) {

                // 4) 타임아웃/연결 설정
                socket.setSoTimeout(timeoutSec * 1000);            // 읽기 타임아웃(밀리초)
                socket.connect(new InetSocketAddress(host, port), timeoutSec * 1000); // 연결

                // 5) SNI(Server Name Indication) 설정
                SSLParameters params = socket.getSSLParameters();
                try {
                    params.setServerNames(java.util.List.of(new SNIHostName(host)));
                } catch (IllegalArgumentException ignore) {
                    // IP 주소 등 SNI 불가 케이스는 무시
                }
                socket.setSSLParameters(params);

                // 6) TLS 핸드셰이크 수행 (서버 인증서 체인을 수신)
                socket.startHandshake();

                // 7) 세션에서 인증서 체인을 얻음
                SSLSession sess = socket.getSession();
                Certificate[] chain = sess.getPeerCertificates();
                if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
                    throw new RuntimeException("서버 인증서 체인을 읽을 수 없습니다.");
                }

                // 8) 리프 인증서의 만료일 추출
                X509Certificate leaf = (X509Certificate) chain[0];
                Instant exp = leaf.getNotAfter().toInstant();          // java.util.Date -> Instant
                long days = ChronoUnit.DAYS.between(Instant.now(), exp); // 남은 일수 계산

                // 9) 결과 채우기
                r.ok = true;
                r.notAfter = exp;
                r.daysLeft = days;
            }
        } catch (Exception e) {
            // 예외(연결 실패, 타임아웃, 핸드셰이크 오류 등) 시 실패로 기록
            r.ok = false;
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            // 10) 경과 시간 기록 (ns -> ms)
            r.elapsedMs = (System.nanoTime() - start) / 1_000_000;
        }
        return r;                                                  // 결과 반환
    }

}
