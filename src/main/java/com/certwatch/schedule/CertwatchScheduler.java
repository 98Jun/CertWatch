package com.certwatch.schedule;

import com.certwatch.config.CertwatchProperties;
import com.certwatch.service.CertCheckerService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 스케줄러 구성 클래스입니다.
 * - @Scheduled(cron = "...") 로 주기 실행
 * - 실제 실행 여부는 properties(certwatch.scheduling.enabled) 플래그로 제어합니다.
 */
@Configuration
public class CertwatchScheduler {

    /** 서비스/설정 주입 */
    private final CertCheckerService service;
    private final CertwatchProperties props;

    /** 생성자 주입 */
    public CertwatchScheduler(CertCheckerService service, CertwatchProperties props) {
        this.service = service;
        this.props = props;
    }

    /**
     * 크론 표현식은 application.properties 의 certwatch.scheduling.cron 에서 주입됩니다.
     * 예: 매일 09:00 → "0 0 9 * * *"
     */
    @Scheduled(cron = "${certwatch.scheduling.cron}")
    public void scheduledCheck() {
        // 스케줄 토글이 꺼져 있으면 아무 것도 하지 않음
//        if (!props.isSchedulingEnabled()) return;

        // 점검 + (조건부) 텔레그램 전송
        service.checkAllAndMaybeNotify();
    }
}
