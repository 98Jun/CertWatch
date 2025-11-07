package com.certwatch.web;

import com.certwatch.entity.CheckDTO;
import com.certwatch.service.DomainCheckerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "CertWatch", description = "인증서 점검 관련 API")
@RestController
@RequestMapping("/api")
public class DomainwatchController {

    @Autowired
    private DomainCheckerService  service;

    /**
     * 즉시 점검을 트리거하는 POST 엔드포인트
     *
     * @return 각 타깃의 점검 결과 리스트(JSON)
     */
    @Operation(summary = "도메인 즉시 점검 실행", description = "설정된 모든 타깃의 TLD 인증서 만료 상태를 즉시 점검합니다.")
    @PostMapping("/check")
    public ResponseEntity<List<CheckDTO>> domainCheckNow(){
        var result = this.service.checkAllAndMaybeNotify();

        return ResponseEntity.ok(result);
    }
}
