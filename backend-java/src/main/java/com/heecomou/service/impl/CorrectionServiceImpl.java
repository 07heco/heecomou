package com.heecomou.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heecomou.mapper.CorrectionHistoryMapper;
import com.heecomou.model.dto.CorrectionRequest;
import com.heecomou.model.entity.CorrectionHistory;
import com.heecomou.service.CorrectionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CorrectionServiceImpl implements CorrectionService {

    private final CorrectionHistoryMapper mapper;

    public CorrectionServiceImpl(CorrectionHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CorrectionHistory record(Long userId, CorrectionRequest request) {
        CorrectionHistory history = new CorrectionHistory();
        history.setUserId(userId);
        history.setOriginalText(request.getOriginalText());
        history.setCorrectedText(request.getCorrectedText());
        history.setSource(request.getSource());

        mapper.insert(history);

        history = mapper.selectById(history.getId());
        return history;
    }

    @Override
    public List<CorrectionHistory> listByUser(Long userId, int limit) {
        LambdaQueryWrapper<CorrectionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CorrectionHistory::getUserId, userId)
               .orderByDesc(CorrectionHistory::getId)
               .last("LIMIT " + Math.min(limit, 100));

        return mapper.selectList(wrapper);
    }
}
