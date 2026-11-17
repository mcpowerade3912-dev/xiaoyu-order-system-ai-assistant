package com.xiaoyu.controller;

import com.xiaoyu.common.Result;
import com.xiaoyu.common.Code;
import com.xiaoyu.dto.AiAssistantRequest;
import com.xiaoyu.service.AiAssistantService;
import com.xiaoyu.service.AiToolService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin/ai")
public class AiAssistantController {
    @Resource
    private AiAssistantService aiAssistantService;
    @Resource
    private AiToolService aiToolService;

    @PostMapping("/assistant")
    public Result assistant(@Validated @RequestBody AiAssistantRequest request) {
        return new Result(Code.SUCCESS, aiAssistantService.ask(request), "查询成功");
    }

    @GetMapping("/status")
    public Result status() {
        return new Result(Code.SUCCESS, aiAssistantService.getStatus(), "查询成功");
    }

    @GetMapping("/overview")
    public Result overview() {
        return new Result(Code.SUCCESS, aiToolService.getDashboardOverview(), "查询成功");
    }
}
