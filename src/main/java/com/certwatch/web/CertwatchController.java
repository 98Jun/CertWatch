package com.certwatch.web;

import com.certwatch.entity.CheckDTO;
import com.certwatch.service.CertCheckerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP API 엔드포인트를 제공하는 컨트롤러 계층입니다.
 * - /api/check POST : 즉시 점검 실행(모든 타깃) 후 결과를 JSON으로 반환합니다.
 */
@Tag(name = "CertWatch", description = "인증서 점검 관련 API")
@RestController
@RequestMapping("/api")
public class CertwatchController {

    /** 서비스 계층 주입 */
    @Autowired
    private final CertCheckerService service;

    /** 생성자 주입 */
    public CertwatchController(CertCheckerService service) {
        this.service = service;
    }

    /**
     * 즉시 점검을 트리거하는 POST 엔드포인트
     * @return 각 타깃의 점검 결과 리스트(JSON)
     */
    @Operation(summary = "SSL 즉시 점검 실행", description = "설정된 모든 타깃의 TLS 인증서 만료 상태를 즉시 점검합니다.")
    @PostMapping("/check")
    public ResponseEntity<List<CheckDTO>> certCheckNow() {
        var results = service.checkAllAndMaybeNotify(); // 서비스 호출 (점검 + 조건부 텔레그램)
        return ResponseEntity.ok(results);              // 200 OK + 결과 바디
    }
}
