package com.xiaoyu.vo;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class OrderStatisticsVO {
    // 今日订单总数
    private Long todayOrderCount;
    // 今日成交总金额
    private BigDecimal todayTotalAmount;
    // 累计订单总数
    private Long totalOrderCount;
    // 累计成交总金额
    private BigDecimal totalAmount;
}
