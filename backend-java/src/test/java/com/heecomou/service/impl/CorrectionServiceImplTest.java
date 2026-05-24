package com.heecomou.service.impl;

import com.heecomou.mapper.CorrectionHistoryMapper;
import com.heecomou.model.dto.CorrectionRequest;
import com.heecomou.model.entity.CorrectionHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrectionServiceImplTest {

    @Mock
    private CorrectionHistoryMapper mapper;

    @InjectMocks
    private CorrectionServiceImpl service;

    @Test
    void record_createsHistory() {
        CorrectionRequest req = new CorrectionRequest();
        req.setOriginalText("人工只能");
        req.setCorrectedText("人工智能");
        req.setSource("manual");

        doAnswer(inv -> {
            CorrectionHistory h = inv.getArgument(0, CorrectionHistory.class);
            h.setId(1L);
            return 1;
        }).when(mapper).insert(any(CorrectionHistory.class));

        CorrectionHistory saved = new CorrectionHistory();
        saved.setId(1L);
        saved.setUserId(100L);
        saved.setOriginalText("人工只能");
        saved.setCorrectedText("人工智能");
        saved.setSource("manual");
        saved.setCreatedAt(LocalDateTime.now());
        when(mapper.selectById(1L)).thenReturn(saved);

        CorrectionHistory result = service.record(100L, req);

        assertNotNull(result);
        assertEquals("人工只能", result.getOriginalText());
        assertEquals("人工智能", result.getCorrectedText());
    }

    @Test
    void listByUser_returnsRecent() {
        CorrectionHistory h1 = new CorrectionHistory();
        h1.setId(1L);
        h1.setOriginalText("a");
        h1.setCorrectedText("b");

        when(mapper.selectList(any())).thenReturn(List.of(h1));

        List<CorrectionHistory> result = service.listByUser(100L, 20);

        assertEquals(1, result.size());
        assertEquals("a", result.get(0).getOriginalText());
    }

    @Test
    void listByUser_limitCapped() {
        when(mapper.selectList(any())).thenReturn(List.of());

        List<CorrectionHistory> result = service.listByUser(100L, 500);

        assertTrue(result.isEmpty());
        verify(mapper).selectList(any());
    }
}
