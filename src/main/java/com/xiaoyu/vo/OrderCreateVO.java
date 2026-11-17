package com.xiaoyu.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class OrderCreateVO {
    private Long orderId;
    private String orderNo;
    private BigDecimal payAmount;
    private Integer status;
    private LocalDateTime paymentDeadline;
}
