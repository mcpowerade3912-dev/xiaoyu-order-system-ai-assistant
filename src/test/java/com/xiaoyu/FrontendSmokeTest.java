package com.xiaoyu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendSmokeTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsPublicAndReferencesFrontendAssets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"login-form\"")))
                .andExpect(content().string(containsString("id=\"view-orders\"")))
                .andExpect(content().string(containsString("id=\"view-products\"")))
                .andExpect(content().string(containsString("id=\"view-users\"")))
                .andExpect(content().string(containsString("id=\"view-addresses\"")))
                .andExpect(content().string(containsString("id=\"view-settings\"")))
                .andExpect(content().string(containsString("id=\"view-ai\"")))
                .andExpect(content().string(containsString("/app.js")))
                .andExpect(content().string(containsString("/styles.css")));
    }

    @Test
    void frontendAssetsArePublic() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk());
    }
}
