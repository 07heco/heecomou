package com.heecomou.model.vo;

import com.heecomou.model.entity.Vocabulary;

import java.time.LocalDateTime;

public class VocabularyVO {

    private Long id;
    private Long userId;
    private String word;
    private String pinyin;
    private String category;
    private Integer frequency;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VocabularyVO from(Vocabulary v) {
        VocabularyVO vo = new VocabularyVO();
        vo.setId(v.getId());
        vo.setUserId(v.getUserId());
        vo.setWord(v.getWord());
        vo.setPinyin(v.getPinyin());
        vo.setCategory(v.getCategory());
        vo.setFrequency(v.getFrequency());
        vo.setVersion(v.getVersion());
        vo.setCreatedAt(v.getCreatedAt());
        vo.setUpdatedAt(v.getUpdatedAt());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getFrequency() { return frequency; }
    public void setFrequency(Integer frequency) { this.frequency = frequency; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
