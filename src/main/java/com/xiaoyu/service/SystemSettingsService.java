package com.xiaoyu.service;

import com.xiaoyu.dto.SystemSettingsDto;

import java.util.Map;

public interface SystemSettingsService {
    SystemSettingsDto getAdminSettings();

    SystemSettingsDto updateSettings(SystemSettingsDto dto, Long adminUserId);

    Map<String, Object> getPublicSettings();

    String getValue(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    String getDecryptedValue(String key, String defaultValue);

    String getAiApiKey(String environmentFallback);

    String getAiBaseUrl(String fallback);

    String getAiModel(String fallback);

    int getAiTimeoutSeconds(int fallback);

    boolean isAiEnabled(boolean fallback);
}
