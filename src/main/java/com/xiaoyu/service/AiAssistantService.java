package com.xiaoyu.service;

import com.xiaoyu.dto.AiAssistantRequest;
import com.xiaoyu.vo.AiAssistantVO;

import java.util.Map;

public interface AiAssistantService {
    AiAssistantVO ask(AiAssistantRequest request);

    Map<String, Object> getStatus();
}
