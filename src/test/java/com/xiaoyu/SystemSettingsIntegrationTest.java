package com.xiaoyu;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.Dao.SystemSettingDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.dto.SystemSettingsDto;
import com.xiaoyu.pojo.SystemSetting;
import com.xiaoyu.pojo.User;
import com.xiaoyu.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ai.openai.api-key=")
@AutoConfigureMockMvc
@Transactional
class SystemSettingsIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserDao userDao;
    @Autowired
    private SystemSettingDao systemSettingDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(IdWorker.getId());
        admin.setUsername("settings_admin_" + System.nanoTime());
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setNickname("设置管理员");
        admin.setPhone("13800138000");
        admin.setRole(1);
        admin.setStatus(1);
        admin.setCreateTime(new Date());
        userDao.insert(admin);
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getRole());
    }

    @Test
    void publicSettingsAreAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.systemName").isNotEmpty());
    }

    @Test
    void adminSettingsPersistAndEncryptApiKey() throws Exception {
        SystemSettingsDto dto = validSettings();
        dto.setAiApiKey("sk-test-secret-key");

        mockMvc.perform(put("/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.systemName").value("不汐汐测试"))
                .andExpect(jsonPath("$.data.aiApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.aiApiKey").doesNotExist());

        SystemSetting stored = systemSettingDao.selectById("ai.apiKey");
        assertNotNull(stored);
        assertNotEquals("sk-test-secret-key", stored.getSettingValue());
        assertFalse(stored.getSettingValue().contains("sk-test-secret-key"));

        mockMvc.perform(get("/admin/ai/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("openai"))
                .andExpect(jsonPath("$.data.model").value("gpt-4.1-mini-test"));
    }

    @Test
    void disablingAiSwitchesAssistantToLocalMode() throws Exception {
        SystemSettingsDto dto = validSettings();
        dto.setAiEnabled(false);
        dto.setAiApiKey("sk-test-disabled");

        mockMvc.perform(put("/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/ai/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("local"))
                .andExpect(jsonPath("$.data.configured").value(false));
    }

    private SystemSettingsDto validSettings() {
        SystemSettingsDto dto = new SystemSettingsDto();
        dto.setSystemName("不汐汐测试");
        dto.setSupportPhone("400-800-1234");
        dto.setPaymentTimeoutMinutes(45);
        dto.setAutoCancelEnabled(true);
        dto.setInventoryWarningThreshold(12);
        dto.setAiEnabled(true);
        dto.setAiBaseUrl("https://api.openai.com/v1");
        dto.setAiModel("gpt-4.1-mini-test");
        dto.setAiTimeoutSeconds(45);
        return dto;
    }
}
