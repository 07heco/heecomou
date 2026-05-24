package com.heecomou.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heecomou.exception.BusinessException;
import com.heecomou.mapper.VocabularyMapper;
import com.heecomou.model.dto.VocabListResponse;
import com.heecomou.model.dto.VocabRequest;
import com.heecomou.model.entity.Vocabulary;
import com.heecomou.model.vo.VocabularyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceImplTest {

    @Mock
    private VocabularyMapper vocabularyMapper;

    @InjectMocks
    private VocabularyServiceImpl vocabularyService;

    private Vocabulary sampleVocab;

    @BeforeEach
    void setUp() {
        sampleVocab = new Vocabulary();
        sampleVocab.setId(1L);
        sampleVocab.setUserId(100L);
        sampleVocab.setWord("人工智能");
        sampleVocab.setPinyin("ren gong zhi neng");
        sampleVocab.setCategory("tech");
        sampleVocab.setFrequency(1);
        sampleVocab.setVersion(1716566400000L);
        sampleVocab.setCreatedAt(LocalDateTime.now());
        sampleVocab.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void add_createsAndReturnsVO() {
        VocabRequest req = new VocabRequest();
        req.setWord("人工智能");
        req.setPinyin("ren gong zhi neng");
        req.setCategory("tech");

        when(vocabularyMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            Vocabulary v = inv.getArgument(0);
            v.setId(1L);
            return 1;
        }).when(vocabularyMapper).insert(any(Vocabulary.class));
        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab);

        VocabularyVO result = vocabularyService.add(100L, req);

        assertNotNull(result);
        assertEquals("人工智能", result.getWord());
        assertEquals(100L, result.getUserId());
        verify(vocabularyMapper).insert(any(Vocabulary.class));
    }

    @Test
    void add_duplicateWord_throws409() {
        VocabRequest req = new VocabRequest();
        req.setWord("人工智能");

        when(vocabularyMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vocabularyService.add(100L, req));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void update_modifiesExisting() {
        VocabRequest req = new VocabRequest();
        req.setWord("机器学习");
        req.setPinyin("ji qi xue xi");
        req.setCategory("tech");

        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab);
        when(vocabularyMapper.selectCount(any())).thenReturn(0L);
        when(vocabularyMapper.updateById(any(Vocabulary.class))).thenReturn(1);

        Vocabulary updated = new Vocabulary();
        updated.setId(1L);
        updated.setUserId(100L);
        updated.setWord("机器学习");
        updated.setPinyin("ji qi xue xi");
        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab, updated);

        VocabularyVO result = vocabularyService.update(100L, 1L, req);

        assertEquals("机器学习", result.getWord());
    }

    @Test
    void update_notFound_throws404() {
        VocabRequest req = new VocabRequest();
        req.setWord("不存在");

        when(vocabularyMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vocabularyService.update(100L, 999L, req));
        assertEquals(404, ex.getCode());
    }

    @Test
    void delete_removesExisting() {
        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab);
        when(vocabularyMapper.deleteById(1L)).thenReturn(1);

        vocabularyService.delete(100L, 1L);

        verify(vocabularyMapper).deleteById(1L);
    }

    @Test
    void delete_notFound_throws404() {
        when(vocabularyMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vocabularyService.delete(100L, 999L));
        assertEquals(404, ex.getCode());
    }

    @Test
    void listByUser_returnsPaged() {
        when(vocabularyMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Vocabulary>(1, 10, 1) {{
                    setRecords(List.of(sampleVocab));
                }});

        VocabListResponse result = vocabularyService.listByUser(100L, 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotal());
    }

    @Test
    void search_byKeyword() {
        when(vocabularyMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Vocabulary>(1, 10, 1) {{
                    setRecords(List.of(sampleVocab));
                }});

        VocabListResponse result = vocabularyService.search(100L, "智能", 1, 10);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
    }

    @Test
    void getById_returnsVO() {
        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab);

        VocabularyVO result = vocabularyService.getById(100L, 1L);

        assertNotNull(result);
        assertEquals("人工智能", result.getWord());
    }

    @Test
    void getById_wrongUser_throws404() {
        sampleVocab.setUserId(200L);
        when(vocabularyMapper.selectById(1L)).thenReturn(sampleVocab);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vocabularyService.getById(100L, 1L));
        assertEquals(404, ex.getCode());
    }
}
