package com.heecomou.model.dto;

import com.heecomou.model.vo.VocabularyVO;

import java.util.List;

public class VocabListResponse {

    private List<VocabularyVO> items;
    private long total;
    private int page;
    private int size;

    public VocabListResponse() {}

    public VocabListResponse(List<VocabularyVO> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<VocabularyVO> getItems() { return items; }
    public void setItems(List<VocabularyVO> items) { this.items = items; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
