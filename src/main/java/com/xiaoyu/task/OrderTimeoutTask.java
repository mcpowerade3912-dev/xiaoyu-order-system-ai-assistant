package com.xiaoyu.task;

import com.xiaoyu.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class OrderTimeoutTask {
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    @Resource
    private OrderService orderService;

    @Scheduled(fixedDelayString = "${order.cancel-scan-ms:60000}",
            initialDelayString = "${order.cancel-scan-initial-delay-ms:60000}")
    public void cancelExpiredOrders() {
        try {
            orderService.cancelExpiredOrders();
        } catch (Exception e) {
            log.error("自动关闭超时订单失败", e);
        }
    }
}
