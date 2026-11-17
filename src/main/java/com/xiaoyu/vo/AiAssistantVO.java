package com.xiaoyu.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiAssistantVO {
    private String answer;
    private String mode;
    private String model;
    private Boolean configured;
    private Long durationMs;
    private LocalDateTime generatedAt;
    private List<AiToolEvidenceVO> evidence = new ArrayList<>();
}
