package com.xiaoyu.service;

import com.xiaoyu.common.Result;

public interface AdminAddressService {
    Result page(Integer pageNum, Integer pageSize, Long userId, String keyword);

    Result detail(Long addressId);
}
