package com.common.service;

import com.certwatch.config.CertwatchProperties;
import com.certwatch.entity.CheckDTO;

import java.net.http.HttpClient;
import java.util.List;

public interface CommonService {

    /**
     * 설정으로부터 타깃 목록을 읽어옵니다.
     * - certwatch.targets : 쉼표 목록
     * - certwatch.targets-file : 라인 파일 (상대/절대 경로 모두 허용)
     */
    List<String> loadTargets(CertwatchProperties props);

    /** "host[:port]" 문자열을 [host, port] 배열로 파싱 (포트 없으면 443) */
    String[] parseTarget(String line);

    /** 텔레그램으로 간단한 텍스트 메시지를 전송합니다. */
    void sendTelegram(HttpClient client, String token, String chatId, String text) throws Exception;

    // null 체크
    boolean stringNullCheck(String obj);

    String formatTelegram(CheckDTO r);
}
