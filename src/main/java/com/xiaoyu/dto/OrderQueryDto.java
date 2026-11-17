package com.xiaoyu.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class OrderQueryDto {
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    // 订单状态筛选：0待付款 1已付款 2已发货 3已完成 4已取消 5已退款
    private Integer status;
}
