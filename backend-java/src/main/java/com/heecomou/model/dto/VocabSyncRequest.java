package com.heecomou.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class VocabSyncRequest {

    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 必须 >= 0")
    private Long version;

    private int limit = 500;

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
}
