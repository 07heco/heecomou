package com.heecomou.model.dto;

import com.heecomou.model.vo.VocabularyVO;

import java.util.List;

public class VocabSyncResponse {

    private List<VocabularyVO> items;
    private boolean hasMore;
    private Long maxVersion;

    public VocabSyncResponse() {}

    public VocabSyncResponse(List<VocabularyVO> items, boolean hasMore, Long maxVersion) {
        this.items = items;
        this.hasMore = hasMore;
        this.maxVersion = maxVersion;
    }

    public List<VocabularyVO> getItems() { return items; }
    public void setItems(List<VocabularyVO> items) { this.items = items; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    public Long getMaxVersion() { return maxVersion; }
    public void setMaxVersion(Long maxVersion) { this.maxVersion = maxVersion; }
}
