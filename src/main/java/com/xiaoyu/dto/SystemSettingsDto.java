package com.xiaoyu.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemSettingsDto {
    private String systemName;
    private String supportPhone;
    private Integer paymentTimeoutMinutes;
    private Boolean autoCancelEnabled;
    private Integer inventoryWarningThreshold;

    private Boolean aiEnabled;
    private String aiBaseUrl;
    private String aiModel;
    private Integer aiTimeoutSeconds;
    private String aiApiKey;
    private Boolean clearAiApiKey;
    private Boolean aiApiKeyConfigured;
    private LocalDateTime updateTime;
}
