package com.heecomou.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heecomou.exception.BusinessException;
import com.heecomou.mapper.VocabularyMapper;
import com.heecomou.model.dto.VocabListResponse;
import com.heecomou.model.dto.VocabRequest;
import com.heecomou.model.entity.Vocabulary;
import com.heecomou.model.vo.VocabularyVO;
import com.heecomou.service.VocabularyService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VocabularyServiceImpl implements VocabularyService {

    private final VocabularyMapper vocabularyMapper;

    public VocabularyServiceImpl(VocabularyMapper vocabularyMapper) {
        this.vocabularyMapper = vocabularyMapper;
    }

    @Override
    public VocabularyVO add(Long userId, VocabRequest request) {
        checkDuplicate(userId, request.getWord());

        Vocabulary v = new Vocabulary();
        v.setUserId(userId);
        v.setWord(request.getWord());
        v.setPinyin(request.getPinyin());
        v.setCategory(request.getCategory());
        v.setFrequency(1);
        v.setVersion(System.currentTimeMillis());

        vocabularyMapper.insert(v);

        v = vocabularyMapper.selectById(v.getId());
        return VocabularyVO.from(v);
    }

    @Override
    public VocabularyVO update(Long userId, Long vocabId, VocabRequest request) {
        Vocabulary v = vocabularyMapper.selectById(vocabId);
        if (v == null || !v.getUserId().equals(userId)) {
            throw new BusinessException(404, "词汇不存在");
        }

        if (!v.getWord().equals(request.getWord())) {
            checkDuplicate(userId, request.getWord());
        }

        v.setWord(request.getWord());
        v.setPinyin(request.getPinyin());
        v.setCategory(request.getCategory());
        v.setVersion(System.currentTimeMillis());

        vocabularyMapper.updateById(v);

        v = vocabularyMapper.selectById(v.getId());
        return VocabularyVO.from(v);
    }

    @Override
    public void delete(Long userId, Long vocabId) {
        Vocabulary v = vocabularyMapper.selectById(vocabId);
        if (v == null || !v.getUserId().equals(userId)) {
            throw new BusinessException(404, "词汇不存在");
        }

        vocabularyMapper.deleteById(vocabId);
    }

    @Override
    public VocabListResponse listByUser(Long userId, int page, int size) {
        LambdaQueryWrapper<Vocabulary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Vocabulary::getUserId, userId)
               .orderByDesc(Vocabulary::getFrequency)
               .orderByDesc(Vocabulary::getId);

        IPage<Vocabulary> result = vocabularyMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<VocabularyVO> items = result.getRecords().stream()
                .map(VocabularyVO::from)
                .toList();

        return new VocabListResponse(items, result.getTotal(), page, size);
    }

    @Override
    public VocabListResponse search(Long userId, String keyword, int page, int size) {
        LambdaQueryWrapper<Vocabulary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Vocabulary::getUserId, userId)
               .and(w -> w.like(Vocabulary::getWord, keyword)
                          .or()
                          .like(Vocabulary::getPinyin, keyword))
               .orderByDesc(Vocabulary::getFrequency)
               .orderByDesc(Vocabulary::getId);

        IPage<Vocabulary> result = vocabularyMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<VocabularyVO> items = result.getRecords().stream()
                .map(VocabularyVO::from)
                .toList();

        return new VocabListResponse(items, result.getTotal(), page, size);
    }

    @Override
    public VocabularyVO getById(Long userId, Long vocabId) {
        Vocabulary v = vocabularyMapper.selectById(vocabId);
        if (v == null || !v.getUserId().equals(userId)) {
            throw new BusinessException(404, "词汇不存在");
        }
        return VocabularyVO.from(v);
    }

    private void checkDuplicate(Long userId, String word) {
        LambdaQueryWrapper<Vocabulary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Vocabulary::getUserId, userId)
               .eq(Vocabulary::getWord, word);
        if (vocabularyMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "该词汇已存在");
        }
    }
}
