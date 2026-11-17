package com.xiaoyu.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

@Data
public class CartDto {
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量不能小于1")
    @Max(value = 9999, message = "商品数量不能超过9999")
    private Integer buyNum;

}
