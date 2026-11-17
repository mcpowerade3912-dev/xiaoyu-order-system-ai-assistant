package com.xiaoyu.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiaoyu.pojo.Order_main;
import com.xiaoyu.vo.AiSalesTrendVO;
import com.xiaoyu.vo.OrderStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMainDao extends BaseMapper<Order_main> {

    /**
     * 订单数据统计
     * status>=1 表示已付款及以上订单计入成交数据
     */
    @Select("SELECT " +
            "COUNT(*) AS totalOrderCount, " +
            "IFNULL(SUM(pay_amount), 0) AS totalAmount, " +
            "IFNULL(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN 1 ELSE 0 END), 0) AS todayOrderCount, " +
            "IFNULL(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN pay_amount ELSE 0 END), 0) AS todayTotalAmount " +
            "FROM order_main " +
            "WHERE status IN (1, 2, 3)")
    OrderStatisticsVO getOrderStatistics();

    /**
     * 原子支付：仅允许订单所有者在有效期内将待付款订单改为待发货。
     */
    @Update("UPDATE order_main SET status = 1, pay_time = NOW(), update_time = NOW(), " +
            "version = version + 1 WHERE id = #{orderId} AND user_id = #{userId} AND status = 0 " +
            "AND create_time >= DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE)")
    int markPaid(@Param("orderId") Long orderId,
                 @Param("userId") Long userId,
                 @Param("timeoutMinutes") Integer timeoutMinutes);

    /**
     * 用户取消待付款订单。状态更新成功后才允许归还库存。
     */
    @Update("UPDATE order_main SET status = 4, update_time = NOW(), version = version + 1 " +
            "WHERE id = #{orderId} AND user_id = #{userId} AND status = 0")
    int cancelUnpaidByUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 系统关闭超时未支付订单。
     */
    @Update("UPDATE order_main SET status = 4, update_time = NOW(), version = version + 1 " +
            "WHERE id = #{orderId} AND status = 0")
    int cancelExpired(@Param("orderId") Long orderId);

    @Select("SELECT id FROM order_main WHERE status = 0 " +
            "AND create_time < DATE_SUB(NOW(), INTERVAL #{timeoutMinutes} MINUTE) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<Long> findExpiredOrderIds(@Param("timeoutMinutes") Integer timeoutMinutes,
                                   @Param("limit") Integer limit);

    @Update("UPDATE order_main SET status = 2, delivery_time = NOW(), update_time = NOW(), " +
            "version = version + 1 WHERE id = #{orderId} AND status = 1")
    int markDelivered(@Param("orderId") Long orderId);

    @Update("UPDATE order_main SET status = 3, finish_time = NOW(), update_time = NOW(), " +
            "version = version + 1 WHERE id = #{orderId} AND user_id = #{userId} AND status = 2")
    int markReceived(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Select("SELECT DATE(create_time) AS sale_date, COUNT(*) AS order_count, " +
            "IFNULL(SUM(pay_amount), 0) AS total_amount FROM order_main " +
            "WHERE status IN (1, 2, 3) AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(create_time) ORDER BY sale_date ASC")
    List<AiSalesTrendVO> getSalesTrend(@Param("days") Integer days);
}
