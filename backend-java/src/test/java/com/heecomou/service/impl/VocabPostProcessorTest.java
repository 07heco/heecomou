package com.heecomou.service.impl;

import com.heecomou.mapper.VocabularyMapper;
import com.heecomou.model.entity.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabPostProcessorTest {

    @Mock
    private VocabularyMapper vocabularyMapper;

    @InjectMocks
    private VocabPostProcessor processor;

    @Test
    void postProcess_emptyText_returnsEmpty() {
        assertEquals("", processor.postProcess(1L, ""));
    }

    @Test
    void postProcess_nullText_returnsNull() {
        assertNull(processor.postProcess(1L, null));
    }

    @Test
    void postProcess_noUserVocab_returnsRaw() {
        when(vocabularyMapper.selectList(any())).thenReturn(List.of());
        assertEquals("hello", processor.postProcess(1L, "hello"));
    }

    @Test
    void correctCommonErrors_replacesKnownErrors() {
        assertEquals("人工智能", processor.correctCommonErrors("人工只能"));
        assertEquals("机器学习", processor.correctCommonErrors("机器血洗"));
        assertEquals("深度学习", processor.correctCommonErrors("深度血洗"));
        assertEquals("自然语言", processor.correctCommonErrors("自燃语言"));
    }

    @Test
    void correctCommonErrors_noMatch_returnsSame() {
        assertEquals("正常文本", processor.correctCommonErrors("正常文本"));
    }

    @Test
    void replaceLongestMatch_withVocab_emptyText() {
        Map<String, Vocabulary> vocab = Map.of();
        String result = processor.replaceLongestMatch("", vocab);
        assertEquals("", result);
    }
}
