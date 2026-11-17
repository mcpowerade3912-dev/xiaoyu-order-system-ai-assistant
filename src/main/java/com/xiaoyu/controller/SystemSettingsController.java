package com.xiaoyu.controller;

import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.dto.SystemSettingsDto;
import com.xiaoyu.service.SystemSettingsService;
import com.xiaoyu.utils.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class SystemSettingsController {
    @Resource
    private SystemSettingsService systemSettingsService;
    @Resource
    private UserContext userContext;

    @GetMapping("/settings/public")
    public Result publicSettings() {
        return new Result(Code.SUCCESS, systemSettingsService.getPublicSettings(), "查询成功");
    }

    @GetMapping("/admin/settings")
    public Result adminSettings() {
        return new Result(Code.SUCCESS, systemSettingsService.getAdminSettings(), "查询成功");
    }

    @PutMapping("/admin/settings")
    public Result updateSettings(@RequestBody SystemSettingsDto dto) {
        return new Result(Code.SUCCESS,
                systemSettingsService.updateSettings(dto, userContext.getUserId()), "保存成功");
    }
}
