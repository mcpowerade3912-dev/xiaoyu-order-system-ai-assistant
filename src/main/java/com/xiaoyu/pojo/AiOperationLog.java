package com.xiaoyu.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_operation_log")
public class AiOperationLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long adminUserId;
    private String question;
    private String answer;
    private String mode;
    private String model;
    private String toolCalls;
    private Integer success;
    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createTime;
}
