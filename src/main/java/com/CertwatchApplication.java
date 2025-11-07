package com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 애플리케이션의 진입점(메인 클래스)입니다.
 * - @SpringBootApplication : 컴포넌트 스캔, 자동 설정, 설정 바인딩 등 부트 핵심을 활성화합니다.
 * - @EnableScheduling     : @Scheduled 가 붙은 메서드를 동작시키는 스케줄러를 켭니다.
 *   (실제로 스케줄이 돌아갈지 여부는 properties의 certwatch.scheduling.enabled 로 제어)
 */
@SpringBootApplication
@EnableScheduling
public class CertwatchApplication {

    /** 자바 애플리케이션 시작 진입점 (내장 톰캣을 띄워 HTTP 서버가 구동됩니다) */
    public static void main(String[] args) {

        SpringApplication.run(CertwatchApplication.class, args);
    }
}
