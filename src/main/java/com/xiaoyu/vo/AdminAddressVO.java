package com.xiaoyu.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAddressVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String name;
    private String phone;
    private String address;
    private Integer isDefault;
    private LocalDateTime createTime;
}
