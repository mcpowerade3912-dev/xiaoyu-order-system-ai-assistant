package com.xiaoyu.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_main")
public class Order_main {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime finishTime;
    private String addressName;
    private String addressPhone;
    private String addressDetail;
    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
