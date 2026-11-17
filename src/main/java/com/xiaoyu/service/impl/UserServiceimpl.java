package com.xiaoyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.User;
import com.xiaoyu.dto.LoginDto;
import com.xiaoyu.dto.UserRegisterDto;
import com.xiaoyu.service.UserService;
import com.xiaoyu.utils.JwtUtil;
import com.xiaoyu.utils.UserContext;
import com.xiaoyu.vo.LoginVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserServiceimpl implements UserService {
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private UserDao userDao;
    @Resource
    private UserContext userContext;

    /*
    * 用户登录功能实现
    * */
    @Override
    public Result UserLogin(LoginDto loginDto) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, loginDto.getUsername().trim());
        User user = userDao.selectOne(queryWrapper);
        if (user == null){
            return new Result(Code.USER_NOT_EXIST,null,"用户不存在");
        }
        if(Integer.valueOf(0).equals(user.getStatus())){
            return new Result(Code.USER_STATUS_ERROR,null,"用户被禁用");
        }
        if (!passwordEncoder.matches(loginDto.getPassword(),user.getPassword())){
            return new Result(Code.PASSWORD_ERROR,null,"密码错误");
        }

        // ==========新增：重置登出时间，清空失效标记==========
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setLogoutTime(null);
        userDao.updateById(updateUser);

        String token = jwtUtil.generateToken(user.getId(),user.getRole());
        LoginVo loginVo = new LoginVo();
        loginVo.setUserId(user.getId());
        loginVo.setUsername(user.getUsername());
        loginVo.setNickname(user.getNickname());
        loginVo.setRole(user.getRole());
        loginVo.setToken(token);
        return new Result(Code.SUCCESS,loginVo,"登录成功");
    }
    /*
    * 用户登出服务实现*/
    @Override
    public Result logout() {
        Long userId = userContext.getUserId();
        //更新用户登出状态，标记token失效
        User updateuser = new User();
        updateuser.setId(userId);
        updateuser.setLogoutTime(LocalDateTime.now());
        userDao.updateById(updateuser);

        return new Result(Code.SUCCESS,userId,"登出成功");

    }
    /*
    * 用户注册服务实现*/
    @Override
    public Result register(UserRegisterDto userRegisterDto) {
        String username = userRegisterDto.getUsername().trim();
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User exitUser = userDao.selectOne(queryWrapper);
        if (exitUser != null){
            return new Result(Code.USERNAME_EXIST,null,"用户已存在");
        }
        //转实体类
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(userRegisterDto.getPassword()));
        user.setNickname(userRegisterDto.getNickname());
        user.setPhone(userRegisterDto.getPhone());
        user.setRole(0);
        user.setStatus(1);
        user.setCreateTime(new Date());

        try {
            userDao.insert(user);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            return new Result(Code.SUCCESS, data, "注册成功");
        } catch (DuplicateKeyException e) {
            return new Result(Code.USERNAME_EXIST, null, "用户已存在");
        }
    }
}
