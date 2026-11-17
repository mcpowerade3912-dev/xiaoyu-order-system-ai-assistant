package com.xiaoyu.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

/*下单主请求Dto*/
@Data
public class OrderMainDto {
    @NotBlank(message = "收件人姓名不能为空")
    @Size(max = 50, message = "收件人姓名不能超过50个字符")
    private String addressName;

    @NotBlank(message = "收件人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "收件人手机号格式不正确")
    private String addressPhone;

    @NotBlank(message = "收件人地址不能为空")
    @Size(max = 255, message = "收件人地址不能超过255个字符")
    private String addressDetail;

    @NotEmpty(message = "商品列表不能为空")
    @Size(max = 50, message = "单笔订单最多包含50种商品")
    @Valid
    private List<OrderItemDto> goodsList;
}
