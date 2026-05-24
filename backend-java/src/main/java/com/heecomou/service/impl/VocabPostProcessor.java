package com.heecomou.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heecomou.mapper.VocabularyMapper;
import com.heecomou.model.entity.Vocabulary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VocabPostProcessor {

    private final VocabularyMapper vocabularyMapper;

    public VocabPostProcessor(VocabularyMapper vocabularyMapper) {
        this.vocabularyMapper = vocabularyMapper;
    }

    public String postProcess(Long userId, String rawText) {
        if (rawText == null || rawText.isBlank()) return rawText;

        Map<String, Vocabulary> userVocab = loadUserVocab(userId);
        if (userVocab.isEmpty()) return rawText;

        String result = replaceLongestMatch(rawText, userVocab);

        result = correctCommonErrors(result);

        return result;
    }

    private Map<String, Vocabulary> loadUserVocab(Long userId) {
        LambdaQueryWrapper<Vocabulary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Vocabulary::getUserId, userId)
               .ge(Vocabulary::getFrequency, 2);

        return vocabularyMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(
                        Vocabulary::getWord,
                        v -> v,
                        (a, b) -> a.getFrequency() >= b.getFrequency() ? a : b
                ));
    }

    String replaceLongestMatch(String text, Map<String, Vocabulary> userVocab) {
        if (text.isEmpty()) return text;

        List<String> sortedWords = userVocab.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());

        StringBuilder result = new StringBuilder(text);
        for (String word : sortedWords) {
            int idx = result.indexOf(word);
            if (idx >= 0) {
                Vocabulary v = userVocab.get(word);
                if (v != null && v.getFrequency() >= 3) {
                    incrementFrequency(v);
                }
            }
        }

        return result.toString();
    }

    String correctCommonErrors(String text) {
        String result = text;

        result = result.replace("人工只能", "人工智能");
        result = result.replace("机器血洗", "机器学习");
        result = result.replace("深度血洗", "深度学习");
        result = result.replace("自燃语言", "自然语言");

        return result;
    }

    private void incrementFrequency(Vocabulary v) {
        v.setFrequency(v.getFrequency() + 1);
        vocabularyMapper.updateById(v);
    }
}
