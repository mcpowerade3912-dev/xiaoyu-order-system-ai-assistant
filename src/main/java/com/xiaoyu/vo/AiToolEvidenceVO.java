package com.xiaoyu.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class AiToolEvidenceVO {
    private String name;
    private Map<String, Object> arguments;
    private Object result;
}
