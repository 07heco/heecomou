package com.heecomou.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VocabRequest {

    @NotBlank(message = "词汇不能为空")
    @Size(max = 100, message = "词汇长度不能超过100")
    private String word;

    @Size(max = 200, message = "拼音长度不能超过200")
    private String pinyin;

    @Size(max = 50, message = "类别长度不能超过50")
    private String category;

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getPinyin() { return pinyin; }
    public void setPinyin(String pinyin) { this.pinyin = pinyin; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
