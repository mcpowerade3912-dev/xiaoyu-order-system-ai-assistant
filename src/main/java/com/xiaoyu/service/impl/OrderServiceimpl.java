package com.xiaoyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.Dao.OrderMainDao;
import com.xiaoyu.common.BusinessException;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.dto.OrderItemDto;
import com.xiaoyu.dto.OrderMainDto;
import com.xiaoyu.dto.OrderQueryDto;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.Order_item;
import com.xiaoyu.pojo.Order_main;
import com.xiaoyu.service.OrderService;
import com.xiaoyu.service.SystemSettingsService;
import com.xiaoyu.utils.UserContext;
import com.xiaoyu.vo.OrderCreateVO;
import com.xiaoyu.vo.OrderDetailVO;
import com.xiaoyu.vo.OrderStatisticsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceimpl implements OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderServiceimpl.class);

    @Resource
    private GoodsDao goodsDao;
    @Resource
    private OrderMainDao orderMainDao;
    @Resource
    private OrderItemDao orderItemDao;
    @Resource
    private UserContext userContext;
    @Resource
    private SystemSettingsService systemSettingsService;

    @Value("${order.payment-timeout-minutes:30}")
    private Integer paymentTimeoutMinutes;

    @Value("${order.expired-scan-batch-size:100}")
    private Integer expiredScanBatchSize;

    /**
     * 用户结算下单。
     * 任一业务校验或库存扣减失败都会抛出异常，确保整笔订单回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createOrder(OrderMainDto orderMainDto) {
        Long userId = userContext.getUserId();
        if (orderMainDto == null || orderMainDto.getGoodsList() == null
                || orderMainDto.getGoodsList().isEmpty()) {
            throw new BusinessException("商品列表不能为空");
        }

        Map<Long, Integer> quantityByGoodsId = aggregateItems(orderMainDto.getGoodsList());
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Order_item> orderItems = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : quantityByGoodsId.entrySet()) {
            Long goodsId = entry.getKey();
            Integer buyNum = entry.getValue();
            Goods goods = goodsDao.selectById(goodsId);
            if (goods == null) {
                throw new BusinessException("商品id" + goodsId + "不存在");
            }
            if (!Integer.valueOf(1).equals(goods.getStatus())) {
                throw new BusinessException("商品" + goods.getGoodsName() + "已下架");
            }
            if (goods.getStock() == null || goods.getStock() < buyNum) {
                throw new BusinessException("商品" + goods.getGoodsName() + "库存不足");
            }

            int row = goodsDao.deductStock(goods.getId(), buyNum, goods.getVersion());
            if (row == 0) {
                throw new BusinessException("商品" + goods.getGoodsName() + "库存发生变化，请刷新后重试");
            }

            totalAmount = totalAmount.add(goods.getPrice().multiply(BigDecimal.valueOf(buyNum)));
            Order_item item = new Order_item();
            item.setGoodsId(goods.getId());
            item.setUserId(userId);
            item.setGoodsName(goods.getGoodsName());
            item.setBuyNum(buyNum);
            item.setGoodsPrice(goods.getPrice());
            orderItems.add(item);
        }

        Long orderId = IdWorker.getId();
        String orderNo = "ORD" + orderId;
        Order_main orderMain = new Order_main();
        orderMain.setId(orderId);
        orderMain.setOrderNo(orderNo);
        orderMain.setUserId(userId);
        orderMain.setTotalAmount(totalAmount);
        orderMain.setPayAmount(totalAmount);
        orderMain.setStatus(0);
        orderMain.setAddressName(orderMainDto.getAddressName().trim());
        orderMain.setAddressPhone(orderMainDto.getAddressPhone().trim());
        orderMain.setAddressDetail(orderMainDto.getAddressDetail().trim());
        orderMainDao.insert(orderMain);

        for (Order_item item : orderItems) {
            item.setOrderNo(orderNo);
            orderItemDao.insert(item);
        }

        LocalDateTime paymentDeadline = LocalDateTime.now().plusMinutes(getPaymentTimeoutMinutes());
        OrderCreateVO vo = new OrderCreateVO(
                orderId, orderNo, totalAmount, 0, paymentDeadline);
        return new Result(Code.SUCCESS, vo, "下单成功");
    }

    @Override
    public Result getMyOrderPage(OrderQueryDto queryDto) {
        Long userId = userContext.getUserId();
        Page<Order_main> page = new Page<>(queryDto.getPageNum(), queryDto.getPageSize());
        LambdaQueryWrapper<Order_main> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order_main::getUserId, userId);
        if (queryDto.getStatus() != null) {
            wrapper.eq(Order_main::getStatus, queryDto.getStatus());
        }
        wrapper.orderByDesc(Order_main::getCreateTime);
        return new Result(Code.SUCCESS, orderMainDao.selectPage(page, wrapper), "查询成功");
    }

    /**
     * 模拟支付。真实接入支付平台后，应由支付回调调用同一状态迁移逻辑。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result payOrder(Long orderId) {
        Long userId = userContext.getUserId();
        int rows = orderMainDao.markPaid(orderId, userId, getPaymentTimeoutMinutes());
        if (rows == 0) {
            Order_main order = orderMainDao.selectById(orderId);
            if (order == null || !userId.equals(order.getUserId())) {
                throw new BusinessException("订单不存在");
            }
            if (Integer.valueOf(4).equals(order.getStatus())) {
                throw new BusinessException("订单已取消或支付超时");
            }
            if (order.getStatus() != null && order.getStatus() >= 1) {
                if (order.getStatus() <= 3) {
                    return new Result(Code.SUCCESS, null, "订单已支付");
                }
            }
            throw new BusinessException("订单当前不可支付，请刷新后重试");
        }
        return new Result(Code.SUCCESS, null, "支付成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelOrder(Long orderId) {
        Long userId = userContext.getUserId();
        int rows = orderMainDao.cancelUnpaidByUser(orderId, userId);
        if (rows == 0) {
            Order_main order = orderMainDao.selectById(orderId);
            if (order == null || !userId.equals(order.getUserId())) {
                throw new BusinessException("订单不存在");
            }
            if (Integer.valueOf(4).equals(order.getStatus())) {
                return new Result(Code.SUCCESS, null, "订单已取消");
            }
            throw new BusinessException("仅待付款订单可取消");
        }
        restoreStockForOrder(orderId);
        return new Result(Code.SUCCESS, null, "订单取消成功");
    }

    /**
     * 定时任务入口：关闭超时未支付订单并归还对应库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelExpiredOrders() {
        if (!systemSettingsService.getBoolean("order.autoCancelEnabled", true)) {
            return 0;
        }
        List<Long> orderIds = orderMainDao.findExpiredOrderIds(
                getPaymentTimeoutMinutes(), expiredScanBatchSize);
        int closedCount = 0;
        for (Long orderId : orderIds) {
            if (orderMainDao.cancelExpired(orderId) == 1) {
                restoreStockForOrder(orderId);
                closedCount++;
            }
        }
        if (closedCount > 0) {
            log.info("已自动关闭 {} 笔超时未支付订单", closedCount);
        }
        return closedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result confirmReceive(Long orderId) {
        Long userId = userContext.getUserId();
        int rows = orderMainDao.markReceived(orderId, userId);
        if (rows == 0) {
            Order_main order = orderMainDao.selectById(orderId);
            if (order == null || !userId.equals(order.getUserId())) {
                throw new BusinessException("订单不存在");
            }
            throw new BusinessException("仅已发货订单可确认收货");
        }
        return new Result(Code.SUCCESS, null, "确认收货成功");
    }

    @Override
    public Result getOrderDetail(Long orderId) {
        Long userId = userContext.getUserId();
        Order_main order = orderMainDao.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("订单不存在");
        }
        return new Result(Code.SUCCESS, buildOrderDetail(order), "查询成功");
    }

    @Override
    public Result getAdminOrderDetail(Long orderId) {
        Order_main order = orderMainDao.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return new Result(Code.SUCCESS, buildOrderDetail(order), "查询成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deliverOrder(Long orderId) {
        int rows = orderMainDao.markDelivered(orderId);
        if (rows == 0) {
            if (orderMainDao.selectById(orderId) == null) {
                throw new BusinessException("订单不存在");
            }
            throw new BusinessException("仅待发货订单可发货");
        }
        return new Result(Code.SUCCESS, null, "发货成功");
    }

    @Override
    public Result adminOrderPage(Integer pageNum, Integer pageSize, Integer status) {
        validatePage(pageNum, pageSize);
        Page<Order_main> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Order_main> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Order_main::getStatus, status);
        }
        wrapper.orderByDesc(Order_main::getCreateTime);
        return new Result(Code.SUCCESS, orderMainDao.selectPage(page, wrapper), "查询成功");
    }

    @Override
    public Result getOrderStatistics() {
        OrderStatisticsVO statistics = orderMainDao.getOrderStatistics();
        return new Result(Code.SUCCESS, statistics, "查询成功");
    }

    private Map<Long, Integer> aggregateItems(List<OrderItemDto> goodsList) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (OrderItemDto item : goodsList) {
            if (item == null || item.getGoodsId() == null
                    || item.getBuyNum() == null || item.getBuyNum() < 1) {
                throw new BusinessException("商品和购买数量不能为空");
            }
            try {
                result.merge(item.getGoodsId(), item.getBuyNum(), Math::addExact);
            } catch (ArithmeticException e) {
                throw new BusinessException("购买数量超出允许范围");
            }
        }
        return result;
    }

    private void restoreStockForOrder(Long orderId) {
        Order_main order = orderMainDao.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        LambdaQueryWrapper<Order_item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order_item::getOrderNo, order.getOrderNo());
        List<Order_item> items = orderItemDao.selectList(wrapper);
        for (Order_item item : items) {
            if (goodsDao.restoreStock(item.getGoodsId(), item.getBuyNum()) == 0) {
                throw new BusinessException("商品" + item.getGoodsName() + "库存归还失败");
            }
        }
    }

    private OrderDetailVO buildOrderDetail(Order_main order) {
        LambdaQueryWrapper<Order_item> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order_item::getOrderNo, order.getOrderNo());
        List<Order_item> itemList = orderItemDao.selectList(wrapper);
        OrderDetailVO detailVO = new OrderDetailVO();
        BeanUtils.copyProperties(order, detailVO);
        detailVO.setItemList(itemList);
        return detailVO;
    }

    private void validatePage(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            throw new BusinessException(Code.PARAM_ERROR, "页码不能小于1");
        }
        if (pageSize == null || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(Code.PARAM_ERROR, "每页条数应在1-100之间");
        }
    }

    private int getPaymentTimeoutMinutes() {
        return systemSettingsService.getInt(
                "order.paymentTimeoutMinutes", paymentTimeoutMinutes);
    }
}
