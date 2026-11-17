package com.xiaoyu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.dto.LoginDto;
import com.xiaoyu.pojo.User;
import com.xiaoyu.service.UserService;
import com.xiaoyu.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDao userDao;

    @BeforeEach
    void setUpAuth() {
        Claims claims = Jwts.claims();
        claims.put("userId", 1L);
        claims.put("role", 0);
        when(jwtUtil.parseToken("valid_token")).thenReturn(claims);

        User user = new User();
        user.setId(1L);
        user.setRole(0);
        user.setStatus(1);
        when(userDao.selectById(1L)).thenReturn(user);
    }

    @Test
    void loginReturnsServiceResult() throws Exception {
        LoginDto dto = new LoginDto();
        dto.setUsername("user");
        dto.setPassword("123456");
        when(userService.UserLogin(dto))
                .thenReturn(new Result(Code.SUCCESS, null, "登录成功"));

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS));
    }

    @Test
    void blankLoginFieldsReturnUnifiedParameterError() throws Exception {
        LoginDto dto = new LoginDto();
        dto.setUsername("");
        dto.setPassword("");

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.PARAM_ERROR));
    }

    @Test
    void logoutRequiresToken() throws Exception {
        mockMvc.perform(get("/user/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }

    @Test
    void logoutCallsServiceForAuthenticatedUser() throws Exception {
        when(userService.logout())
                .thenReturn(new Result(Code.SUCCESS, 1L, "登出成功"));

        mockMvc.perform(get("/user/logout")
                        .header("Authorization", "Bearer valid_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Code.SUCCESS))
                .andExpect(jsonPath("$.msg").value("登出成功"));

        verify(userService).logout();
    }

    @Test
    void invalidTokenReturnsUnauthorizedCode() throws Exception {
        when(jwtUtil.parseToken("invalid_token"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        mockMvc.perform(get("/user/logout")
                        .header("Authorization", "Bearer invalid_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("401"));
    }
}
