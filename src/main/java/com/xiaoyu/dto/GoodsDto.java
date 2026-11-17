package com.xiaoyu.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
public class GoodsDto {
    //页码
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;
    //每页数量
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;
    //商品名称
    private String goodsName;
    //管理端筛选：0下架 1上架
    @Min(value = 0, message = "商品状态仅支持0或1")
    @Max(value = 1, message = "商品状态仅支持0或1")
    private Integer status;
    //库存不高于该值
    @Min(value = 0, message = "库存阈值不能小于0")
    private Integer stockThreshold;
}
