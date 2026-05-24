package com.heecomou.model.dto;

import jakarta.validation.constraints.NotBlank;

public class CorrectionRequest {

    @NotBlank(message = "原始文本不能为空")
    private String originalText;

    @NotBlank(message = "纠正后文本不能为空")
    private String correctedText;

    private String source = "manual";

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
