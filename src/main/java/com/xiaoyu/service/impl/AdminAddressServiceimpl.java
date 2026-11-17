package com.xiaoyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyu.Dao.UserAddressDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.common.BusinessException;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.pojo.User;
import com.xiaoyu.pojo.UserAddress;
import com.xiaoyu.service.AdminAddressService;
import com.xiaoyu.vo.AdminAddressVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminAddressServiceimpl implements AdminAddressService {
    @Resource
    private UserAddressDao userAddressDao;
    @Resource
    private UserDao userDao;

    @Override
    public Result page(Integer pageNum, Integer pageSize, Long userId, String keyword) {
        if (pageNum == null || pageNum < 1) {
            throw new BusinessException(Code.PARAM_ERROR, "页码不能小于1");
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(Code.PARAM_ERROR, "每页条数应在1-100之间");
        }

        LambdaQueryWrapper<UserAddress> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(UserAddress::getUserId, userId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String search = keyword.trim();
            wrapper.and(query -> query
                    .like(UserAddress::getName, search)
                    .or().like(UserAddress::getPhone, search)
                    .or().like(UserAddress::getAddress, search));
        }
        wrapper.orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime);

        Page<UserAddress> source = userAddressDao.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<Long, User> users = loadUsers(source.getRecords());
        List<AdminAddressVO> records = source.getRecords().stream()
                .map(address -> toVO(address, users.get(address.getUserId())))
                .collect(Collectors.toList());

        Page<AdminAddressVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(records);
        result.setPages(source.getPages());
        return new Result(Code.SUCCESS, result, "查询成功");
    }

    @Override
    public Result detail(Long addressId) {
        UserAddress address = userAddressDao.selectById(addressId);
        if (address == null) {
            throw new BusinessException("地址不存在");
        }
        return new Result(Code.SUCCESS, toVO(address, userDao.selectById(address.getUserId())), "查询成功");
    }

    private Map<Long, User> loadUsers(List<UserAddress> addresses) {
        List<Long> userIds = addresses.stream()
                .map(UserAddress::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userDao.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminAddressVO toVO(UserAddress address, User user) {
        AdminAddressVO vo = new AdminAddressVO();
        BeanUtils.copyProperties(address, vo);
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        return vo;
    }
}
