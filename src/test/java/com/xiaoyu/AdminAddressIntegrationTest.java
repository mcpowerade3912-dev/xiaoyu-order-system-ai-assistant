package com.xiaoyu;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xiaoyu.Dao.UserAddressDao;
import com.xiaoyu.Dao.UserDao;
import com.xiaoyu.pojo.User;
import com.xiaoyu.pojo.UserAddress;
import com.xiaoyu.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAddressIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserAddressDao userAddressDao;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    private User admin;
    private User customer;
    private UserAddress address;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        admin = createUser("address_admin_" + suffix, 1);
        customer = createUser("address_customer_" + suffix, 0);
        adminToken = jwtUtil.generateToken(admin.getId(), admin.getRole());
        customerToken = jwtUtil.generateToken(customer.getId(), customer.getRole());

        address = new UserAddress();
        address.setId(IdWorker.getId());
        address.setUserId(customer.getId());
        address.setName("地址测试用户");
        address.setPhone("13800138000");
        address.setAddress("北京市海淀区测试地址 101 号");
        address.setIsDefault(1);
        userAddressDao.insert(address);
    }

    @AfterEach
    void cleanUp() {
        userAddressDao.deleteById(address.getId());
        userDao.deleteById(customer.getId());
        userDao.deleteById(admin.getId());
    }

    @Test
    void adminCanSearchCustomerAddresses() throws Exception {
        mockMvc.perform(get("/admin/address/page")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .param("keyword", "地址测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(customer.getUsername()))
                .andExpect(jsonPath("$.data.records[0].isDefault").value(1));
    }

    @Test
    void adminCanFilterAddressesByUserIdAndReadDetail() throws Exception {
        mockMvc.perform(get("/admin/address/page")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", String.valueOf(customer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(customer.getId()));

        mockMvc.perform(get("/admin/address/" + address.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value(address.getAddress()))
                .andExpect(jsonPath("$.data.nickname").value(customer.getNickname()));
    }

    @Test
    void normalUserCannotAccessAdminAddressBook() throws Exception {
        mockMvc.perform(get("/admin/address/page")
                        .header("Authorization", "Bearer " + customerToken))
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
