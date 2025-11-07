package com.common.service.impl;

import com.certwatch.config.CertwatchProperties;
import com.certwatch.entity.CheckDTO;
import com.common.service.CommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service("CommonService")
public class CommonServiceImpl implements CommonService {

    /**
     * 설정으로부터 타깃 목록을 읽어옵니다.
     * - certwatch.targets : 쉼표 목록
     * - certwatch.targets-file : 라인 파일 (상대/절대 경로 모두 허용)
     */
    public List<String> loadTargets(CertwatchProperties props) {
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

    /** "host[:port]" 문자열을 [host, port] 배열로 파싱 (포트 없으면 443) */
    public String[] parseTarget(String line) {
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
    public void sendTelegram(HttpClient client, String token, String chatId, String text) throws Exception {
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

    //스트링 널 체크
    public boolean stringNullCheck(String obj){
        boolean result = true;
        if(obj == null || obj.isBlank()){
            result= false;
        }
        return result;
    }

    /** 알림 메시지 포맷 (HTML 파싱 모드) */
    public String formatTelegram(CheckDTO r) {
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
}
