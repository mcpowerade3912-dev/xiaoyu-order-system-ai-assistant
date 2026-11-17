package com.xiaoyu.service.impl;

import com.xiaoyu.Dao.SystemSettingDao;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyu.common.BusinessException;
import com.xiaoyu.common.Code;
import com.xiaoyu.dto.SystemSettingsDto;
import com.xiaoyu.pojo.SystemSetting;
import com.xiaoyu.service.SystemSettingsService;
import com.xiaoyu.utils.SettingCrypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemSettingsServiceimpl implements SystemSettingsService {
    private static final String SYSTEM_NAME = "system.name";
    private static final String SUPPORT_PHONE = "system.supportPhone";
    private static final String PAYMENT_TIMEOUT = "order.paymentTimeoutMinutes";
    private static final String AUTO_CANCEL = "order.autoCancelEnabled";
    private static final String INVENTORY_THRESHOLD = "inventory.warningThreshold";
    private static final String AI_ENABLED = "ai.enabled";
    private static final String AI_BASE_URL = "ai.baseUrl";
    private static final String AI_MODEL = "ai.model";
    private static final String AI_TIMEOUT = "ai.timeoutSeconds";
    private static final String AI_API_KEY = "ai.apiKey";

    @Resource
    private SystemSettingDao systemSettingDao;
    @Resource
    private SettingCrypto settingCrypto;

    @Override
    public SystemSettingsDto getAdminSettings() {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setSystemName(getValue(SYSTEM_NAME, "不汐汐"));
        dto.setSupportPhone(getValue(SUPPORT_PHONE, ""));
        dto.setPaymentTimeoutMinutes(getInt(PAYMENT_TIMEOUT, 30));
        dto.setAutoCancelEnabled(getBoolean(AUTO_CANCEL, true));
        dto.setInventoryWarningThreshold(getInt(INVENTORY_THRESHOLD, 10));
        dto.setAiEnabled(getBoolean(AI_ENABLED, true));
        dto.setAiBaseUrl(getValue(AI_BASE_URL, "https://api.openai.com/v1"));
        dto.setAiModel(getValue(AI_MODEL, "gpt-4.1-mini"));
        dto.setAiTimeoutSeconds(getInt(AI_TIMEOUT, 60));
        String encryptedKey = settingValue(AI_API_KEY);
        boolean keyConfigured = encryptedKey != null && !encryptedKey.isBlank();
        if (keyConfigured) {
            try {
                settingCrypto.decrypt(encryptedKey);
            } catch (Exception e) {
                keyConfigured = false;
            }
        }
        dto.setAiApiKeyConfigured(keyConfigured);
        LambdaQueryWrapper<SystemSetting> latestWrapper = new LambdaQueryWrapper<>();
        latestWrapper.orderByDesc(SystemSetting::getUpdateTime).last("LIMIT 1");
        SystemSetting lastUpdated = systemSettingDao.selectOne(latestWrapper);
        if (lastUpdated != null) {
            dto.setUpdateTime(lastUpdated.getUpdateTime());
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemSettingsDto updateSettings(SystemSettingsDto dto, Long adminUserId) {
        if (dto == null) {
            throw new BusinessException(Code.PARAM_ERROR, "设置内容不能为空");
        }
        validate(dto);
        upsert(SYSTEM_NAME, dto.getSystemName().trim(), false, adminUserId);
        upsert(SUPPORT_PHONE, trimToEmpty(dto.getSupportPhone()), false, adminUserId);
        upsert(PAYMENT_TIMEOUT, String.valueOf(dto.getPaymentTimeoutMinutes()), false, adminUserId);
        upsert(AUTO_CANCEL, String.valueOf(dto.getAutoCancelEnabled()), false, adminUserId);
        upsert(INVENTORY_THRESHOLD, String.valueOf(dto.getInventoryWarningThreshold()), false, adminUserId);
        upsert(AI_ENABLED, String.valueOf(dto.getAiEnabled()), false, adminUserId);
        upsert(AI_BASE_URL, trimToEmpty(dto.getAiBaseUrl()), false, adminUserId);
        upsert(AI_MODEL, dto.getAiModel().trim(), false, adminUserId);
        upsert(AI_TIMEOUT, String.valueOf(dto.getAiTimeoutSeconds()), false, adminUserId);

        if (Boolean.TRUE.equals(dto.getClearAiApiKey())) {
            systemSettingDao.deleteById(AI_API_KEY);
        } else if (dto.getAiApiKey() != null && !dto.getAiApiKey().isBlank()) {
            upsert(AI_API_KEY, settingCrypto.encrypt(dto.getAiApiKey().trim()), true, adminUserId);
        }
        return getAdminSettings();
    }

    @Override
    public Map<String, Object> getPublicSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemName", getValue(SYSTEM_NAME, "不汐汐"));
        result.put("supportPhone", getValue(SUPPORT_PHONE, ""));
        return result;
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String value = settingValue(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = settingValue(key);
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    public String getDecryptedValue(String key, String defaultValue) {
        String value = settingValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        SystemSetting setting = systemSettingDao.selectById(key);
        return setting != null && Integer.valueOf(1).equals(setting.getIsSecret())
                ? settingCrypto.decrypt(value)
                : value;
    }

    public String getAiApiKey(String environmentFallback) {
        return getDecryptedValue(AI_API_KEY, environmentFallback);
    }

    public String getAiBaseUrl(String fallback) {
        return getValue(AI_BASE_URL, fallback);
    }

    public String getAiModel(String fallback) {
        return getValue(AI_MODEL, fallback);
    }

    public int getAiTimeoutSeconds(int fallback) {
        return getInt(AI_TIMEOUT, fallback);
    }

    public boolean isAiEnabled(boolean fallback) {
        return getBoolean(AI_ENABLED, fallback);
    }

    private void upsert(String key, String value, boolean secret, Long adminUserId) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setIsSecret(secret ? 1 : 0);
        setting.setUpdatedBy(adminUserId);
        setting.setUpdateTime(LocalDateTime.now());
        if (systemSettingDao.selectById(key) == null) {
            systemSettingDao.insert(setting);
        } else {
            systemSettingDao.updateById(setting);
        }
    }

    private String settingValue(String key) {
        SystemSetting setting = systemSettingDao.selectById(key);
        return setting == null ? null : setting.getSettingValue();
    }

    private void validate(SystemSettingsDto dto) {
        if (dto.getSystemName() == null || dto.getSystemName().trim().isEmpty()
                || dto.getSystemName().trim().length() > 30) {
            throw new BusinessException(Code.PARAM_ERROR, "系统名称长度应在1-30个字符之间");
        }
        if (dto.getSupportPhone() != null && dto.getSupportPhone().length() > 30) {
            throw new BusinessException(Code.PARAM_ERROR, "客服联系方式不能超过30个字符");
        }
        if (dto.getPaymentTimeoutMinutes() == null
                || dto.getPaymentTimeoutMinutes() < 5 || dto.getPaymentTimeoutMinutes() > 1440) {
            throw new BusinessException(Code.PARAM_ERROR, "支付超时时间应在5-1440分钟之间");
        }
        if (dto.getAutoCancelEnabled() == null) {
            throw new BusinessException(Code.PARAM_ERROR, "自动关单设置不能为空");
        }
        if (dto.getInventoryWarningThreshold() == null || dto.getInventoryWarningThreshold() < 0) {
            throw new BusinessException(Code.PARAM_ERROR, "库存预警阈值不能小于0");
        }
        if (dto.getAiEnabled() == null || dto.getAiBaseUrl() == null
                || dto.getAiBaseUrl().isBlank() || dto.getAiModel() == null
                || dto.getAiModel().isBlank() || dto.getAiTimeoutSeconds() == null) {
            throw new BusinessException(Code.PARAM_ERROR, "AI 接口配置不完整");
        }
        try {
            URI uri = URI.create(dto.getAiBaseUrl().trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessException(Code.PARAM_ERROR, "AI 接口地址必须是有效的 HTTP/HTTPS URL");
        }
        if (dto.getAiModel().trim().length() > 100) {
            throw new BusinessException(Code.PARAM_ERROR, "AI 模型名称不能超过100个字符");
        }
        if (dto.getAiTimeoutSeconds() < 5 || dto.getAiTimeoutSeconds() > 180) {
            throw new BusinessException(Code.PARAM_ERROR, "AI 超时时间应在5-180秒之间");
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
