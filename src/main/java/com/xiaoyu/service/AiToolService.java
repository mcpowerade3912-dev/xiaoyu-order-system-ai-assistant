package com.xiaoyu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.Dao.OrderMainDao;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.Order_item;
import com.xiaoyu.pojo.Order_main;
import com.xiaoyu.vo.AiSalesTrendVO;
import com.xiaoyu.vo.AiToolEvidenceVO;
import com.xiaoyu.vo.OrderStatisticsVO;
import com.xiaoyu.service.SystemSettingsService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiToolService {
    @Resource
    private OrderMainDao orderMainDao;
    @Resource
    private OrderItemDao orderItemDao;
    @Resource
    private GoodsDao goodsDao;
    @Resource
    private SystemSettingsService systemSettingsService;

    public Map<String, Object> execute(String toolName, Map<String, Object> arguments) {
        if ("get_business_overview".equals(toolName)) {
            return getBusinessOverview();
        }
        if ("query_orders".equals(toolName)) {
            return queryOrders(
                    intValue(arguments, "status"),
                    decimalValue(arguments, "minAmount"),
                    stringValue(arguments, "createdAfter"),
                    stringValue(arguments, "orderKeyword"),
                    intValue(arguments, "limit"));
        }
        if ("query_low_stock_goods".equals(toolName)) {
            return queryLowStockGoods(
                    intValue(arguments, "threshold"),
                    intValue(arguments, "limit"));
        }
        if ("get_sales_trend".equals(toolName)) {
            return getSalesTrend(intValue(arguments, "days"));
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "不支持的工具：" + toolName);
        return error;
    }

    public Map<String, Object> getDashboardOverview() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("overview", getBusinessOverview());
        dashboard.put("lowStock", queryLowStockGoods(inventoryWarningThreshold(), 6));
        return dashboard;
    }

    public LocalAnalysis localAnalyze(String message) {
        String text = message.toLowerCase();
        List<ToolCall> calls = new ArrayList<>();

        if (containsAny(text, "概览", "经营", "总览", "汇总", "概况", "概況")) {
            calls.add(new ToolCall("get_business_overview", new LinkedHashMap<>()));
        }
        if (containsAny(text, "库存", "缺货", "补货")) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("threshold", firstNumber(text, inventoryWarningThreshold()));
            arguments.put("limit", 10);
            calls.add(new ToolCall("query_low_stock_goods", arguments));
        }
        if (containsAny(text, "趋势", "走势", "最近", "本周", "七天", "7天")) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("days", parseDays(text));
            calls.add(new ToolCall("get_sales_trend", arguments));
        }

        Integer orderStatus = null;
        if (containsAny(text, "待发货", "未发货")) {
            orderStatus = 1;
        } else if (containsAny(text, "待付款", "未付款")) {
            orderStatus = 0;
        } else if (containsAny(text, "已发货", "运输中")) {
            orderStatus = 2;
        } else if (containsAny(text, "已完成", "已收货")) {
            orderStatus = 3;
        } else if (containsAny(text, "已取消", "取消的订单")) {
            orderStatus = 4;
        }
        BigDecimal minAmount = extractAmount(text);
        if (orderStatus != null || minAmount.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (orderStatus != null) {
                arguments.put("status", orderStatus);
            }
            if (minAmount.compareTo(BigDecimal.ZERO) > 0) {
                arguments.put("minAmount", minAmount);
            }
            arguments.put("limit", 10);
            calls.add(new ToolCall("query_orders", arguments));
        }
        if (calls.isEmpty()) {
            calls.add(new ToolCall("get_business_overview", new LinkedHashMap<>()));
        }

        List<AiToolEvidenceVO> evidence = new ArrayList<>();
        List<String> answers = new ArrayList<>();
        for (ToolCall call : calls) {
            Map<String, Object> result = execute(call.getName(), call.getArguments());
            evidence.add(new AiToolEvidenceVO(call.getName(), call.getArguments(), result));
            answers.add(buildLocalAnswer(call.getName(), result));
        }
        return new LocalAnalysis(String.join("\n\n", answers), evidence);
    }

    private Map<String, Object> getBusinessOverview() {
        OrderStatisticsVO statistics = orderMainDao.getOrderStatistics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayOrderCount", valueOrZero(statistics == null ? null : statistics.getTodayOrderCount()));
        result.put("todayTotalAmount", valueOrZero(statistics == null ? null : statistics.getTodayTotalAmount()));
        result.put("totalOrderCount", valueOrZero(statistics == null ? null : statistics.getTotalOrderCount()));
        result.put("totalAmount", valueOrZero(statistics == null ? null : statistics.getTotalAmount()));
        result.put("pendingPaymentCount", countOrdersByStatus(0));
        result.put("pendingShipmentCount", countOrdersByStatus(1));
        result.put("shippedCount", countOrdersByStatus(2));
        result.put("cancelledCount", countOrdersByStatus(4));
        result.put("activeGoodsCount", countActiveGoods());
        result.put("lowStockGoodsCount", countLowStockGoods(inventoryWarningThreshold()));
        result.put("generatedAt", LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> queryOrders(Integer status, BigDecimal minAmount,
                                           String createdAfter, String keyword, Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<Order_main> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Order_main::getStatus, status);
        }
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) > 0) {
            wrapper.ge(Order_main::getPayAmount, minAmount);
        }
        if (createdAfter != null && !createdAfter.isBlank()) {
            try {
                wrapper.ge(Order_main::getCreateTime, LocalDate.parse(createdAfter).atStartOfDay());
            } catch (Exception ignored) {
                // Ignore a malformed optional filter and keep the assistant usable.
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Order_main::getOrderNo, keyword.trim());
        }
        wrapper.orderByDesc(Order_main::getCreateTime);

        Page<Order_main> page = orderMainDao.selectPage(new Page<>(1, safeLimit), wrapper);
        Map<String, List<Order_item>> itemMap = loadOrderItems(page.getRecords());
        List<Map<String, Object>> orders = page.getRecords().stream()
                .map(order -> toOrderMap(order, itemMap.getOrDefault(order.getOrderNo(), Collections.emptyList())))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("limit", safeLimit);
        result.put("status", status);
        result.put("statusName", statusName(status));
        result.put("orders", orders);
        return result;
    }

    private Map<String, Object> queryLowStockGoods(Integer threshold, Integer limit) {
        int safeThreshold = threshold == null ? inventoryWarningThreshold() : Math.max(0, threshold);
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Goods::getStatus, 1)
                .le(Goods::getStock, safeThreshold)
                .orderByAsc(Goods::getStock)
                .orderByDesc(Goods::getSales);
        Page<Goods> page = goodsDao.selectPage(new Page<>(1, safeLimit), wrapper);

        List<Map<String, Object>> goodsList = page.getRecords().stream().map(goods -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("goodsId", goods.getId());
            item.put("goodsName", goods.getGoodsName());
            item.put("stock", goods.getStock());
            item.put("sales", goods.getSales());
            item.put("price", goods.getPrice());
            item.put("status", goods.getStatus());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("threshold", safeThreshold);
        result.put("goods", goodsList);
        return result;
    }

    private Map<String, Object> getSalesTrend(Integer days) {
        int safeDays = days == null ? 7 : Math.max(1, Math.min(days, 90));
        List<AiSalesTrendVO> trend = orderMainDao.getSalesTrend(Math.max(0, safeDays - 1));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("trend", trend);
        return result;
    }

    private String buildLocalAnswer(String toolName, Map<String, Object> result) {
        if ("get_business_overview".equals(toolName)) {
            return "当前经营概览：今日成交 " + result.get("todayOrderCount") + " 笔，金额 "
                    + result.get("todayTotalAmount") + " 元；待付款 " + result.get("pendingPaymentCount")
                    + " 笔，待发货 " + result.get("pendingShipmentCount") + " 笔；在售商品 "
                    + result.get("activeGoodsCount") + " 件，其中库存不高于 10 件的商品 "
                    + result.get("lowStockGoodsCount") + " 件。";
        }
        if ("query_low_stock_goods".equals(toolName)) {
            List<Map<String, Object>> goods = castList(result.get("goods"));
            if (goods.isEmpty()) {
                return "当前没有库存不高于 " + result.get("threshold") + " 件的在售商品。";
            }
            String details = goods.stream().limit(8)
                    .map(item -> item.get("goodsName") + "（库存 " + item.get("stock") + "）")
                    .collect(Collectors.joining("、"));
            return "库存偏低的商品共 " + result.get("total") + " 件：" + details + "。";
        }
        if ("query_orders".equals(toolName)) {
            List<Map<String, Object>> orders = castList(result.get("orders"));
            if (orders.isEmpty()) {
                return "没有找到符合条件的订单。";
            }
            String details = orders.stream().limit(8)
                    .map(order -> order.get("orderNo") + "（" + order.get("statusName") + "，"
                            + order.get("payAmount") + " 元）")
                    .collect(Collectors.joining("；"));
            return "共找到 " + result.get("total") + " 笔订单。最近记录：" + details + "。";
        }
        if ("get_sales_trend".equals(toolName)) {
            List<AiSalesTrendVO> trend = castTrend(result.get("trend"));
            if (trend.isEmpty()) {
                return "所选周期内暂无已支付订单数据。";
            }
            String details = trend.stream()
                    .map(item -> item.getSaleDate() + "：" + item.getOrderCount() + " 笔，"
                            + item.getTotalAmount() + " 元")
                    .collect(Collectors.joining("；"));
            return "最近 " + result.get("days") + " 天成交趋势：" + details + "。";
        }
        return "已完成数据查询。";
    }

    private long countOrdersByStatus(int status) {
        LambdaQueryWrapper<Order_main> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order_main::getStatus, status);
        return orderMainDao.selectCount(wrapper);
    }

    private long countActiveGoods() {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Goods::getStatus, 1);
        return goodsDao.selectCount(wrapper);
    }

    private long countLowStockGoods(int threshold) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Goods::getStatus, 1).le(Goods::getStock, threshold);
        return goodsDao.selectCount(wrapper);
    }

    private int inventoryWarningThreshold() {
        return systemSettingsService.getInt("inventory.warningThreshold", 10);
    }

    private Map<String, List<Order_item>> loadOrderItems(List<Order_main> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> orderNos = orders.stream()
                .map(Order_main::getOrderNo)
                .collect(Collectors.toList());
        LambdaQueryWrapper<Order_item> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Order_item::getOrderNo, orderNos);
        return orderItemDao.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(Order_item::getOrderNo));
    }

    private Map<String, Object> toOrderMap(Order_main order, List<Order_item> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("statusName", statusName(order.getStatus()));
        result.put("payAmount", order.getPayAmount());
        result.put("createTime", order.getCreateTime());
        result.put("items", items.stream()
                .map(item -> item.getGoodsName() + " x" + item.getBuyNum())
                .collect(Collectors.joining("、")));
        return result;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "全部";
        }
        switch (status) {
            case 0:
                return "待付款";
            case 1:
                return "待发货";
            case 2:
                return "已发货";
            case 3:
                return "已完成";
            case 4:
                return "已取消";
            default:
                return "未知状态";
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Integer firstNumber(String text, int defaultValue) {
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : defaultValue;
    }

    private BigDecimal extractAmount(String text) {
        Matcher matcher = Pattern.compile("(?:超过|大于|高于|至少)\\s*(\\d+(?:\\.\\d+)?)").matcher(text);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : BigDecimal.ZERO;
    }

    private int parseDays(String text) {
        int number = firstNumber(text, 7);
        return Math.max(1, Math.min(number, 90));
    }

    private Integer intValue(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return null;
        }
        return new BigDecimal(arguments.get(key).toString()).intValue();
    }

    private BigDecimal decimalValue(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return null;
        }
        return new BigDecimal(arguments.get(key).toString());
    }

    private String stringValue(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.get(key) == null) {
            return null;
        }
        return arguments.get(key).toString();
    }

    private Object valueOrZero(Object value) {
        return value == null ? 0 : value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List ? (List<Map<String, Object>>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<AiSalesTrendVO> castTrend(Object value) {
        return value instanceof List ? (List<AiSalesTrendVO>) value : Collections.emptyList();
    }

    @Data
    @AllArgsConstructor
    public static class LocalAnalysis {
        private String answer;
        private List<AiToolEvidenceVO> evidence;
    }

    @Data
    @AllArgsConstructor
    private static class ToolCall {
        private String name;
        private Map<String, Object> arguments;
    }
}
