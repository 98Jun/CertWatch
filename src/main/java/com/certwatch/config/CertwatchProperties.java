package com.certwatch.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * application.properties 의 "certwatch.*" 키들을 객체로 바인딩하는 설정 클래스입니다.
 * - @Component : 컴포넌트 스캔 대상으로 등록 (빈으로 관리)
 * - @ConfigurationProperties(prefix = "certwatch") : "certwatch." 접두사의 속성을 이 클래스 필드에 주입
 */
@Component
@ConfigurationProperties(prefix = "certwatch")
@ToString
@Getter
@Setter
public class CertwatchProperties {

    /** 스케줄러 활성화 여부 (true면 cron에 따라 자동 실행) */
    private boolean schedulingEnabled = true;

    /** 스케줄 실행 주기 (Spring Cron 식) */
    private String schedulingCron = "0 0 9 * * *";

    /** 만료 임계치(일) : 이 값 이하로 남았으면 알림 대상 */
    private int thresholdDays = 30;

    /** 호스트당 소켓 타임아웃(초) */
    private int timeoutSeconds = 10;

    /** 동시 체크 스레드 수 */
    private int workers = 20;

    /** 쉼표로 나열한 타깃 호스트 목록 (host 또는 host:port) */
    private List<String> targets = new ArrayList<>();

    /** 한 줄 한 타깃의 파일 경로 (상대/절대 모두 가능) */
    private String targetsFile = null;

    /** 텔레그램 관련 설정 (token/chatId) */
    private Telegram telegram = new Telegram();

    /** 내부 클래스로 텔레그램 설정을 캡슐화 */
    @ToString
    @Getter
    @Setter
    public static class Telegram {
        /** 봇 토큰 (ex. 123456:ABCDEF...) */
        private String token;
        /** 메시지를 받을 개인/그룹의 Chat ID */
        private String chatId;

    }

}

