package com.xiaoyu.controller;

import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.service.AdminAddressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/address")
public class AdminAddressController {
    @Resource
    private AdminAddressService adminAddressService;

    @GetMapping("/page")
    public Result page(@RequestParam(defaultValue = "1") Integer pageNum,
                       @RequestParam(defaultValue = "10") Integer pageSize,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(required = false) String keyword) {
        return adminAddressService.page(pageNum, pageSize, userId, keyword);
    }

    @GetMapping("/{addressId}")
    public Result detail(@PathVariable Long addressId) {
        return adminAddressService.detail(addressId);
    }
}
