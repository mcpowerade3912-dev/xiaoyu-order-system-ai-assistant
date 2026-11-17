package com.xiaoyu.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaoyu.Dao.CartDao;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.Dao.OrderItemDao;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.Order_item;
import com.xiaoyu.pojo.UserCart;
import com.xiaoyu.dto.GoodsDto;
import com.xiaoyu.service.GoodsService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GoodsServiceimpl implements GoodsService {
    @Resource
    private GoodsDao goodsDao;
    @Resource
    private CartDao cartDao;
    @Resource
    private OrderItemDao orderItemDao;
    /**
     * 买家端商品分页查询实现
     * @param dto
     * @return
     */
    @Override
    public Page<Goods> getGoodsPage(GoodsDto dto){
        //构建分页对象
        Page<Goods> page = new Page<>(dto.getPageNum(),dto.getPageSize());
        //构建查询条件
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        //买家端只展示上架商品
        queryWrapper.eq(Goods::getStatus,1);
        //商品名字模糊查询
        if(!StringUtils.isEmpty(dto.getGoodsName())){
            queryWrapper.like(Goods::getGoodsName,dto.getGoodsName());
        }
        //默认倒叙
        queryWrapper.orderByDesc(Goods::getCreateTime);

        //执行分页返回结果
        return goodsDao.selectPage(page,queryWrapper);
    };

    /**
     * 管理端商品分页查询实现
     * @param dto
     * @return
     */
    @Override
    public Page<Goods> adminGoodsPage(GoodsDto dto) {
        //新建分页对象
        Page<Goods> page = new Page<>(dto.getPageNum(),dto.getPageSize());
        //构建查询条件
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        if(dto.getGoodsName()!=null && !StringUtils.isEmpty(dto.getGoodsName().trim())){
            queryWrapper.like(Goods::getGoodsName,dto.getGoodsName());
        }
        if (dto.getStatus() != null) {
            queryWrapper.eq(Goods::getStatus, dto.getStatus());
        }
        if (dto.getStockThreshold() != null) {
            queryWrapper.le(Goods::getStock, dto.getStockThreshold());
        }
        queryWrapper.orderByDesc(Goods::getCreateTime);
        return goodsDao.selectPage(page,queryWrapper);
    }

    /**
     * 管理端添加商品实现
     * @param goods
     * @return
     */
    @Override
    public Result addGoods(Goods goods) {
        String validationError = validateGoodsForCreate(goods);
        if (validationError != null) {
            return new Result(Code.PARAM_ERROR, null, validationError);
        }
        goods.setGoodsName(goods.getGoodsName().trim());
        // 1. 查重：判断商品名称是否已存在
        LambdaQueryWrapper<Goods> repeatWrapper = new LambdaQueryWrapper<>();
        repeatWrapper.eq(Goods::getGoodsName, goods.getGoodsName().trim());
        long count = goodsDao.selectCount(repeatWrapper);
        if (count > 0) {
            return new Result(Code.FAIL, null, "该商品名称已存在，禁止重复添加");
        }
        // 新增商品默认配置：版本号1、上架状态、初始销量0
        goods.setVersion(1);
        if (goods.getStatus() == null) {
            goods.setStatus(1);
        }
        if (goods.getStock() == null) {
            goods.setStock(0);
        }
        if (goods.getSales() == null) {
            goods.setSales(0);
        }
        try {
            goodsDao.insert(goods);
        } catch (DuplicateKeyException e) {
            return new Result(Code.FAIL, null, "该商品名称已存在，禁止重复添加");
        }
        return new Result(Code.SUCCESS, null, "商品新增成功");
    }

    /**
     * 管理端修改商品实现
     * @param goods
     * @return
     */
    @Override
    public Result updateGoods(Goods goods) {
        // 1. 基础参数校验
        if (goods.getId() == null) {
            return new Result(Code.FAIL, null, "商品ID不能为空");
        }
        if (goods.getVersion() == null) {
            return new Result(Code.FAIL, null, "版本号不能为空，请刷新页面后重试");
        }
        String validationError = validateGoodsForUpdate(goods);
        if (validationError != null) {
            return new Result(Code.PARAM_ERROR, null, validationError);
        }
        String repeatMsg = "该商品名称已存在，请勿重复使用";

        // 2. 商品名称查重：排除自身ID，不能和其他商品重名
        if (goods.getGoodsName() != null && !goods.getGoodsName().trim().isEmpty()) {
            goods.setGoodsName(goods.getGoodsName().trim());
            LambdaQueryWrapper<Goods> repeatWrapper = new LambdaQueryWrapper<>();
            repeatWrapper.eq(Goods::getGoodsName, goods.getGoodsName().trim())
                    .ne(Goods::getId, goods.getId()); // 排除自己
            long count = goodsDao.selectCount(repeatWrapper);
            if (count > 0) {
                return new Result(Code.FAIL, null, repeatMsg);
            }
        }

        // 禁止手动修改销量，版本号保留前端传来的值用于乐观锁校验
        goods.setSales(null);
        // 不要 goods.setVersion(null); 这行删掉！

        try {
            // 执行更新，接收影响行数
            int row = goodsDao.updateById(goods);
            if (row == 0) {
                // 乐观锁冲突：版本号不一致，数据已被其他人修改
                return new Result(Code.FAIL, null, "商品信息已被他人修改，请刷新页面重新编辑");
            }
        } catch (DuplicateKeyException e) {
            // 并发修改同名商品触发唯一索引冲突兜底
            return new Result(Code.FAIL, null, repeatMsg);
        }
        return new Result(Code.SUCCESS, null, "商品修改成功");
    }

    /**
     * 管理端修改商品状态实现
     * @param goodsId
     * @param status
     * @return
     */
    @Override
    public Result updateGoodsStatus(Long goodsId, Integer status) {
        if (goodsId == null) {
            return new Result(Code.FAIL, null, "商品ID不能为空");
        }
        if (status == null || status != 0 && status != 1) {
            return new Result(Code.FAIL, null, "状态参数非法，仅支持0下架/1上架");
        }

        Goods goods = goodsDao.selectById(goodsId);
        if (goods == null) {
            return new Result(Code.FAIL, null, "商品不存在");
        }

        LambdaUpdateWrapper<Goods> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Goods::getId, goodsId)
                .eq(Goods::getVersion, goods.getVersion()) // 乐观锁条件
                .set(Goods::getStatus, status);

        int rows = goodsDao.update(null, wrapper);
        if (rows == 0) {
            return new Result(Code.FAIL, null, "商品信息已被其他用户修改，请刷新后重试");
        }

        String msg = status == 1 ? "商品上架成功" : "商品下架成功";
        return new Result(Code.SUCCESS, null, msg);
    }

    private String validateGoodsForCreate(Goods goods) {
        if (goods == null || goods.getGoodsName() == null || goods.getGoodsName().trim().isEmpty()) {
            return "商品名称不能为空";
        }
        if (goods.getGoodsName().trim().length() > 200) {
            return "商品名称不能超过200个字符";
        }
        if (goods.getPrice() == null || goods.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "商品价格必须大于0";
        }
        if (goods.getStock() != null && goods.getStock() < 0) {
            return "商品库存不能小于0";
        }
        if (goods.getStatus() != null && goods.getStatus() != 0 && goods.getStatus() != 1) {
            return "商品状态仅支持0下架/1上架";
        }
        return null;
    }

    private String validateGoodsForUpdate(Goods goods) {
        if (goods.getGoodsName() != null) {
            if (goods.getGoodsName().trim().isEmpty()) {
                return "商品名称不能为空";
            }
            if (goods.getGoodsName().trim().length() > 200) {
                return "商品名称不能超过200个字符";
            }
        }
        if (goods.getPrice() != null && goods.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "商品价格必须大于0";
        }
        if (goods.getStock() != null && goods.getStock() < 0) {
            return "商品库存不能小于0";
        }
        if (goods.getStatus() != null && goods.getStatus() != 0 && goods.getStatus() != 1) {
            return "商品状态仅支持0下架/1上架";
        }
        return null;
    }

    @Override
    public Result getGoodsDetail(Long goodsId) {
        if (goodsId == null) {
            return new Result(Code.PARAM_ERROR, null, "商品ID不能为空");
        }
        Goods goods = goodsDao.selectById(goodsId);
        if (goods == null) {
            return new Result(Code.FAIL, null, "商品不存在");
        }
        return new Result(Code.SUCCESS, goods, "查询成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteGoods(Long goodsId) {
        Goods goods = goodsDao.selectById(goodsId);
        if (goods == null) {
            return new Result(Code.FAIL, null, "商品不存在");
        }
        if (Integer.valueOf(1).equals(goods.getStatus())) {
            return new Result(Code.FAIL, null, "请先下架商品再删除");
        }

        LambdaQueryWrapper<Order_item> orderItemWrapper = new LambdaQueryWrapper<>();
        orderItemWrapper.eq(Order_item::getGoodsId, goodsId);
        if (orderItemDao.selectCount(orderItemWrapper) > 0) {
            return new Result(Code.FAIL, null, "商品已被历史订单引用，只能下架，不能删除");
        }

        LambdaQueryWrapper<UserCart> cartWrapper = new LambdaQueryWrapper<>();
        cartWrapper.eq(UserCart::getGoodsId, goodsId);
        cartDao.delete(cartWrapper);
        if (goodsDao.deleteById(goodsId) == 0) {
            return new Result(Code.FAIL, null, "商品删除失败，请刷新后重试");
        }
        return new Result(Code.SUCCESS, null, "商品删除成功");
    }

    @Override
    public Map<String, Object> getAdminGoodsStatistics() {
        LambdaQueryWrapper<Goods> total = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<Goods> active = new LambdaQueryWrapper<>();
        active.eq(Goods::getStatus, 1);
        LambdaQueryWrapper<Goods> offShelf = new LambdaQueryWrapper<>();
        offShelf.eq(Goods::getStatus, 0);
        LambdaQueryWrapper<Goods> lowStock = new LambdaQueryWrapper<>();
        lowStock.eq(Goods::getStatus, 1).le(Goods::getStock, 10);
        LambdaQueryWrapper<Goods> outOfStock = new LambdaQueryWrapper<>();
        outOfStock.eq(Goods::getStatus, 1).le(Goods::getStock, 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", goodsDao.selectCount(total));
        result.put("active", goodsDao.selectCount(active));
        result.put("offShelf", goodsDao.selectCount(offShelf));
        result.put("lowStock", goodsDao.selectCount(lowStock));
        result.put("outOfStock", goodsDao.selectCount(outOfStock));
        return result;
    }
}
