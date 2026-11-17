package com.xiaoyu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.Dao.OrderMainDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.Order_item;
import com.xiaoyu.pojo.Order_main;
import com.xiaoyu.pojo.User;
import com.xiaoyu.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderClosureIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserDao userDao;
    @Autowired
    private GoodsDao goodsDao;
    @Autowired
    private OrderMainDao orderMainDao;
    @Autowired
    private OrderItemDao orderItemDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    private User buyer;
    private User admin;
    private Goods goods;
    private String buyerToken;
    private String adminToken;
    private Long createdOrderId;
    private String createdOrderNo;

    @BeforeEach
    void setUpData() {
        String suffix = String.valueOf(System.nanoTime());
        buyer = createUser("closure_buyer_" + suffix, 0);
        admin = createUser("closure_admin_" + suffix, 1);
        buyerToken = jwtUtil.generateToken(buyer.getId(), buyer.getRole());
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getRole());

        goods = new Goods();
        goods.setId(IdWorker.getId());
        goods.setGoodsName("closure_goods_" + suffix);
        goods.setPrice(new BigDecimal("12.34"));
        goods.setStock(20);
        goods.setSales(0);
        goods.setVersion(1);
        goods.setStatus(1);
        goodsDao.insert(goods);
    }

    @AfterEach
    void cleanUpData() {
        if (createdOrderNo != null) {
            LambdaQueryWrapper<Order_item> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(Order_item::getOrderNo, createdOrderNo);
            orderItemDao.delete(itemWrapper);
        }
        if (createdOrderId != null) {
            orderMainDao.deleteById(createdOrderId);
        }
        LambdaQueryWrapper<Order_main> buyerOrders = new LambdaQueryWrapper<>();
        buyerOrders.eq(Order_main::getUserId, buyer.getId());
        orderMainDao.delete(buyerOrders);
        goodsDao.deleteById(goods.getId());
        userDao.deleteById(buyer.getId());
        userDao.deleteById(admin.getId());
    }

    @Test
    void fullOrderLifecycle() throws Exception {
        createOrder(2);
        assertEquals(0, orderMainDao.selectById(createdOrderId).getStatus());
        assertEquals(18, goodsDao.selectById(goods.getId()).getStock());
        assertEquals(2, goodsDao.selectById(goods.getId()).getSales());

        mockMvc.perform(post("/order/pay/" + createdOrderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));

        Order_main paid = orderMainDao.selectById(createdOrderId);
        assertEquals(1, paid.getStatus());
        assertNotNull(paid.getPayTime());

        mockMvc.perform(put("/admin/order/deliver/" + createdOrderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
        assertEquals(2, orderMainDao.selectById(createdOrderId).getStatus());

        mockMvc.perform(put("/order/confirm/" + createdOrderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
        assertEquals(3, orderMainDao.selectById(createdOrderId).getStatus());
    }

    @Test
    void cancelRestoresStockOnlyOnce() throws Exception {
        createOrder(3);
        assertEquals(17, goodsDao.selectById(goods.getId()).getStock());

        mockMvc.perform(put("/order/cancel/" + createdOrderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("订单取消成功"));

        assertEquals(4, orderMainDao.selectById(createdOrderId).getStatus());
        assertEquals(20, goodsDao.selectById(goods.getId()).getStock());

        mockMvc.perform(put("/order/cancel/" + createdOrderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("订单已取消"));

        assertEquals(20, goodsDao.selectById(goods.getId()).getStock());
    }

    @Test
    void failedOrderCreationRollsBackStockAndOrder() throws Exception {
        String body = orderBody(goods.getId(), 4, "999999999999");
        mockMvc.perform(post("/order/create")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500"));

        Goods unchanged = goodsDao.selectById(goods.getId());
        assertEquals(20, unchanged.getStock());
        assertEquals(0, unchanged.getSales());

        LambdaQueryWrapper<Order_main> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order_main::getUserId, buyer.getId());
        assertEquals(0, orderMainDao.selectCount(wrapper));
    }

    @Test
    void expiredOrderCannotBePaid() throws Exception {
        createOrder(1);
        jdbcTemplate.update(
                "UPDATE order_main SET create_time = DATE_SUB(NOW(), INTERVAL 2 HOUR) WHERE id = ?",
                createdOrderId);

        mockMvc.perform(post("/order/pay/" + createdOrderId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500"));

        assertEquals(0, orderMainDao.selectById(createdOrderId).getStatus());
        assertEquals(19, goodsDao.selectById(goods.getId()).getStock());
    }

    private User createUser(String username, int role) {
        User user = new User();
        user.setId(IdWorker.getId());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode("123456"));
        user.setNickname(username);
        user.setPhone("13800138000");
        user.setRole(role);
        user.setStatus(1);
        user.setCreateTime(new Date());
        userDao.insert(user);
        return user;
    }

    private void createOrder(int buyNum) throws Exception {
        MvcResult result = mockMvc.perform(post("/order/create")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(goods.getId(), buyNum, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.orderId").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        createdOrderId = data.get("orderId").asLong();
        createdOrderNo = data.get("orderNo").asText();
    }

    private String orderBody(Long firstGoodsId, int buyNum, String secondGoodsId) {
        String secondItem = secondGoodsId == null
                ? ""
                : ",{\"goodsId\":" + secondGoodsId + ",\"buyNum\":1}";
        return "{\"addressName\":\"张三\",\"addressPhone\":\"13800138000\"," +
                "\"addressDetail\":\"北京市海淀区测试地址\",\"goodsList\":[" +
                "{\"goodsId\":" + firstGoodsId + ",\"buyNum\":" + buyNum + "}" +
                secondItem + "]}";
    }
}
