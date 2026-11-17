package com.xiaoyu.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AiSalesTrendVO {
    private LocalDate saleDate;
    private Long orderCount;
    private BigDecimal totalAmount;
}
