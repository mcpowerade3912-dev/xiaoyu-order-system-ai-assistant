package com.xiaoyu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xiaoyu.Dao.AiOperationLogDao;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.Dao.OrderMainDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.AiOperationLog;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ai.openai.api-key=")
@AutoConfigureMockMvc
class AiAssistantIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserDao userDao;
    @Autowired
    private GoodsDao goodsDao;
    @Autowired
    private OrderMainDao orderMainDao;
    @Autowired
    private OrderItemDao orderItemDao;
    @Autowired
    private AiOperationLogDao aiOperationLogDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    private User admin;
    private User normalUser;
    private Goods goods;
    private Order_main paidOrder;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        admin = createUser("ai_admin_" + suffix, 1);
        normalUser = createUser("ai_user_" + suffix, 0);
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getRole());
        userToken = jwtUtil.generateToken(normalUser.getId(), normalUser.getRole());

        goods = new Goods();
        goods.setId(IdWorker.getId());
        goods.setGoodsName("ai_low_stock_" + suffix);
        goods.setPrice(new BigDecimal("39.90"));
        goods.setStock(3);
        goods.setSales(5);
        goods.setVersion(1);
        goods.setStatus(1);
        goodsDao.insert(goods);

        paidOrder = new Order_main();
        paidOrder.setId(IdWorker.getId());
        paidOrder.setOrderNo("AI" + paidOrder.getId());
        paidOrder.setUserId(normalUser.getId());
        paidOrder.setTotalAmount(new BigDecimal("39.90"));
        paidOrder.setPayAmount(new BigDecimal("39.90"));
        paidOrder.setStatus(1);
        paidOrder.setPayTime(LocalDateTime.now());
        paidOrder.setAddressName("测试用户");
        paidOrder.setAddressPhone("13800138000");
        paidOrder.setAddressDetail("测试地址");
        paidOrder.setVersion(1);
        orderMainDao.insert(paidOrder);

        Order_item item = new Order_item();
        item.setOrderNo(paidOrder.getOrderNo());
        item.setGoodsId(goods.getId());
        item.setUserId(normalUser.getId());
        item.setGoodsName(goods.getGoodsName());
        item.setGoodsPrice(goods.getPrice());
        item.setBuyNum(1);
        orderItemDao.insert(item);
    }

    @AfterEach
    void cleanUp() {
        LambdaQueryWrapper<AiOperationLog> logs = new LambdaQueryWrapper<>();
        logs.eq(AiOperationLog::getAdminUserId, admin.getId());
        aiOperationLogDao.delete(logs);

        LambdaQueryWrapper<Order_item> items = new LambdaQueryWrapper<>();
        items.eq(Order_item::getOrderNo, paidOrder.getOrderNo());
        orderItemDao.delete(items);
        orderMainDao.deleteById(paidOrder.getId());
        goodsDao.deleteById(goods.getId());
        userDao.deleteById(normalUser.getId());
        userDao.deleteById(admin.getId());
    }

    @Test
    void statusReportsLocalModeWithoutApiKey() throws Exception {
        mockMvc.perform(get("/admin/ai/status")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.mode").value("local"));
    }

    @Test
    void adminCanLoadDashboardOverviewWithoutCallingModel() throws Exception {
        mockMvc.perform(get("/admin/ai/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.overview.pendingShipmentCount").isNumber())
                .andExpect(jsonPath("$.data.lowStock.total").isNumber());
    }

    @Test
    void adminCanAskBusinessOverview() throws Exception {
        mockMvc.perform(post("/admin/ai/assistant")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"请给我一份经营概览，并提示库存风险\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.mode").value("local"))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.evidence[0].name").value("get_business_overview"))
                .andExpect(jsonPath("$.data.evidence[1].name").value("query_low_stock_goods"));

        LambdaQueryWrapper<AiOperationLog> logs = new LambdaQueryWrapper<>();
        logs.eq(AiOperationLog::getAdminUserId, admin.getId());
        assertEquals(1, aiOperationLogDao.selectCount(logs));
    }

    @Test
    void adminCanAskLowStockGoods() throws Exception {
        mockMvc.perform(post("/admin/ai/assistant")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"库存低于5的商品有哪些\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.mode").value("local"))
                .andExpect(jsonPath("$.data.evidence[0].name").value("query_low_stock_goods"))
                .andExpect(jsonPath("$.data.answer").value(org.hamcrest.Matchers.containsString(goods.getGoodsName())));
    }

    @Test
    void normalUserCannotAccessAiAssistant() throws Exception {
        mockMvc.perform(post("/admin/ai/assistant")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"查看经营概览\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("403"));
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
}
