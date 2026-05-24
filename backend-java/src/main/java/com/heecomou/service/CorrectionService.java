package com.heecomou.service;

import com.heecomou.model.dto.CorrectionRequest;
import com.heecomou.model.entity.CorrectionHistory;

import java.util.List;

public interface CorrectionService {

    CorrectionHistory record(Long userId, CorrectionRequest request);

    List<CorrectionHistory> listByUser(Long userId, int limit);
}
