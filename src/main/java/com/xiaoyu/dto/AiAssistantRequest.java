package com.xiaoyu.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class AiAssistantRequest {
    @NotBlank(message = "问题不能为空")
    @Size(min = 2, max = 500, message = "问题长度应在2-500个字符之间")
    private String message;
}
