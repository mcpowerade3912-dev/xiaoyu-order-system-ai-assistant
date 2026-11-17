package com.xiaoyu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.Order_item;
import com.xiaoyu.pojo.User;
import com.xiaoyu.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserDao userDao;
    @Autowired
    private GoodsDao goodsDao;
    @Autowired
    private OrderItemDao orderItemDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    private User admin;
    private Goods product;
    private String token;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        admin = new User();
        admin.setId(IdWorker.getId());
        admin.setUsername("product_admin_" + suffix);
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setNickname("商品测试管理员");
        admin.setPhone("13800138000");
        admin.setRole(1);
        admin.setStatus(1);
        admin.setCreateTime(new Date());
        userDao.insert(admin);
        token = jwtUtil.generateToken(admin.getId(), admin.getRole());

        product = createProduct("product_test_" + suffix, 1);
    }

    @AfterEach
    void cleanUp() {
        if (product.getId() != null) {
            LambdaQueryWrapper<Order_item> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(Order_item::getGoodsId, product.getId());
            orderItemDao.delete(itemWrapper);
            goodsDao.deleteById(product.getId());
        }
        userDao.deleteById(admin.getId());
    }

    @Test
    void adminCanReadProductDetailAndStatistics() throws Exception {
        mockMvc.perform(get("/admin/detail/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goodsName").value(product.getGoodsName()));

        mockMvc.perform(get("/admin/goods/statistics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.active").isNumber())
                .andExpect(jsonPath("$.data.lowStock").isNumber());
    }

    @Test
    void offShelfProductWithoutOrderReferencesCanBeDeleted() throws Exception {
        product.setStatus(0);
        goodsDao.updateById(product);

        mockMvc.perform(delete("/admin/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.msg").value("商品删除成功"));

        assertNull(goodsDao.selectById(product.getId()));
        product.setId(null);
    }

    @Test
    void onShelfOrOrderReferencedProductCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/admin/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.msg").value("请先下架商品再删除"));

        product.setStatus(0);
        goodsDao.updateById(product);
        Order_item item = new Order_item();
        item.setOrderNo("PRODUCT-REF-" + product.getId());
        item.setGoodsId(product.getId());
        item.setUserId(admin.getId());
        item.setGoodsName(product.getGoodsName());
        item.setGoodsPrice(product.getPrice());
        item.setBuyNum(1);
        orderItemDao.insert(item);

        mockMvc.perform(delete("/admin/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.msg").value("商品已被历史订单引用，只能下架，不能删除"));

        assertNotNull(goodsDao.selectById(product.getId()));
    }

    private Goods createProduct(String name, int status) {
        Goods goods = new Goods();
        goods.setId(IdWorker.getId());
        goods.setGoodsName(name);
        goods.setPrice(new BigDecimal("19.90"));
        goods.setStock(5);
        goods.setSales(0);
        goods.setVersion(1);
        goods.setStatus(status);
        goodsDao.insert(goods);
        return goods;
    }
}
