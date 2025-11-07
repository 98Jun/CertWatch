package com.certwatch.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * 한 개 대상(host:port)의 점검 결과를 담는 DTO입니다.
 * 컨트롤러에서 JSON으로 직렬화되어 응답됩니다.
 */
@Getter
@Setter
@ToString
public class CheckDTO {
    /** 확인 타입 SSL or Domain */
    public String type;
    /** 호스트 이름 */
    public String host;
    /** 포트 번호 */
    public int port;
    /** 점검 성공 여부 */
    public boolean ok;
    /** 실패 시 오류 메시지 */
    public String error;
    /** 인증서 만료 시각(UTC, ISO-8601) */
    public Instant notAfter;
    /** 만료까지 남은 일수 */
    public long daysLeft;
    /** 처리 시간(ms) */
    public long elapsedMs;
}
