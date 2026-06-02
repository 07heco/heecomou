package com.heecomou.service;

import com.heecomou.model.dto.VocabSyncRequest;
import com.heecomou.model.dto.VocabSyncResponse;
import com.heecomou.model.dto.VocabListResponse;
import com.heecomou.model.dto.VocabRequest;
import com.heecomou.model.vo.VocabularyVO;

import java.util.List;

public interface VocabularyService {

    VocabularyVO add(Long userId, VocabRequest request);

    VocabularyVO update(Long userId, Long vocabId, VocabRequest request);

    void delete(Long userId, Long vocabId);

    VocabListResponse listByUser(Long userId, int page, int size, String category);

    VocabListResponse search(Long userId, String keyword, int page, int size, String category);

    VocabularyVO getById(Long userId, Long vocabId);

    VocabSyncResponse sync(Long userId, VocabSyncRequest request);

    List<String> listCategories(Long userId);
}
