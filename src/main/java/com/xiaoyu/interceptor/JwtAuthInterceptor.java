package com.xiaoyu.interceptor;

import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.User;
import com.xiaoyu.utils.JwtUtil;
import com.xiaoyu.utils.UserContext;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    @Autowired
    private UserDao userDao;
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        response.setContentType("application/json;charset=utf-8");
        String authorization = request.getHeader("Authorization");
        //判断token是否为空
        if(authorization == null || !authorization.startsWith("Bearer ")){
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录，请登录\",\"data\":null}");
            return false;
        }
        String token = authorization.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录，请登录\",\"data\":null}");
            return false;
        }

        // 读取 userId（JSON 反序列化后可能是 Integer 或 Long）
        Long userId;
        Object userIdObj = claims.get("userId");
        if (userIdObj == null) {
            response.getWriter().write("{\"code\":401,\"msg\":\"无效的登录凭证\",\"data\":null}");
            return false;
        }
        if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else {
            userId = (Long) userIdObj;
        }

        User user = userDao.selectById(userId);
        //判断用户是否存在
        if(user==null){
            response.getWriter().write("{\"code\":401,\"msg\":\"用户不存在，请重新登录\",\"data\":null}");
            return false;
        }
        if(user.getLogoutTime()!=null){
            response.getWriter().write("{\"code\":401,\"msg\":\"用户已登出，请重新登录\",\"data\":null}");
            return false;
        }
        if(!Integer.valueOf(1).equals(user.getStatus())){
            response.getWriter().write("{\"code\":403,\"msg\":\"用户已被冻结\",\"data\":null}");
            return false;
        }

        // 角色以数据库当前值为准，避免管理员降权后旧 Token 仍可访问
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/admin") && !Integer.valueOf(1).equals(user.getRole())) {
            response.getWriter().write("{\"code\":403,\"msg\":\"权限不足，该功能仅管理员可用\",\"data\":null}");
            return false;
        }

        claims.put("role", user.getRole());
        UserContext.setUser(claims);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }
}
