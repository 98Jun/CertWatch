package com.certwatch.service;

import com.certwatch.entity.CheckDTO;

import java.util.List;

public interface DomainCheckerService {
    List<CheckDTO> checkAllAndMaybeNotify();
}
