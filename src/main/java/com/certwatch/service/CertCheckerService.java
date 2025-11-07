package com.certwatch.service;

import com.certwatch.entity.CheckDTO;

import java.util.List;

/**
 * 인증서 만료 점검 및 (조건부) 텔레그램 전송을 담당하는 서비스 계층입니다.
 * - 순수 로직을 담당하여 웹/스케줄러 등 여러 진입점에서 재사용 가능합니다.
 */
public interface CertCheckerService {

    List<CheckDTO>  checkAllAndMaybeNotify();
}
