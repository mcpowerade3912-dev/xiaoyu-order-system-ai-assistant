package com.xiaoyu.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_setting")
public class SystemSetting {
    @TableId
    private String settingKey;
    private String settingValue;
    private Integer isSecret;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
