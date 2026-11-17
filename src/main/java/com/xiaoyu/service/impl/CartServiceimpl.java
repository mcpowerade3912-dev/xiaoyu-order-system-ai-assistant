package com.xiaoyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiaoyu.Dao.CartDao;
import com.xiaoyu.Dao.GoodsDao;
import com.xiaoyu.common.BusinessException;
import com.xiaoyu.common.Code;
import com.xiaoyu.common.Result;
import com.xiaoyu.dto.CartDto;
import com.xiaoyu.pojo.Goods;
import com.xiaoyu.pojo.UserCart;
import com.xiaoyu.service.CartService;
import com.xiaoyu.utils.UserContext;
import com.xiaoyu.vo.CartVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CartServiceimpl implements CartService {
    @Resource
    private CartDao cartDao;
    @Resource
    private GoodsDao goodsDao;
    @Resource
    private UserContext userContext;

    @Override
    public Result addCart(CartDto cartDto) {
        Long userId = userContext.getUserId();
        Goods goods = requireAvailableGoods(cartDto.getGoodsId());
        UserCart cart = findCart(userId, cartDto.getGoodsId());

        int totalNum = cartDto.getBuyNum();
        if (cart != null) {
            try {
                totalNum = Math.addExact(cart.getBuyNum(), cartDto.getBuyNum());
            } catch (ArithmeticException e) {
                throw new BusinessException("购物车商品数量超出允许范围");
            }
        }
        if (totalNum > goods.getStock()) {
            throw new BusinessException("商品库存不足");
        }

        if (cart == null) {
            cart = new UserCart();
            cart.setUserId(userId);
            cart.setGoodsId(cartDto.getGoodsId());
            cart.setBuyNum(totalNum);
            cartDao.insert(cart);
        } else {
            cart.setBuyNum(totalNum);
            cartDao.updateById(cart);
        }
        return new Result(Code.SUCCESS, toCartVo(cart, goods), "加入购物车成功");
    }

    @Override
    public Result getListCart() {
        Long userId = userContext.getUserId();
        LambdaQueryWrapper<UserCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCart::getUserId, userId)
                .orderByDesc(UserCart::getCreateTime);
        List<UserCart> carts = cartDao.selectList(wrapper);
        if (carts.isEmpty()) {
            return new Result(Code.SUCCESS, Collections.emptyList(), "查询购物车成功");
        }

        Set<Long> goodsIds = carts.stream()
                .map(UserCart::getGoodsId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Goods> goodsMap = goodsDao.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, Function.identity()));
        List<CartVo> result = carts.stream()
                .map(cart -> toCartVo(cart, goodsMap.get(cart.getGoodsId())))
                .collect(Collectors.toList());
        return new Result(Code.SUCCESS, result, "查询购物车成功");
    }

    @Override
    public Result updateCartNum(CartDto dto) {
        Long userId = userContext.getUserId();
        UserCart cart = findCart(userId, dto.getGoodsId());
        if (cart == null) {
            throw new BusinessException("购物车记录不存在");
        }
        Goods goods = requireAvailableGoods(dto.getGoodsId());
        if (dto.getBuyNum() > goods.getStock()) {
            throw new BusinessException("商品库存不足");
        }
        cart.setBuyNum(dto.getBuyNum());
        cartDao.updateById(cart);
        return new Result(Code.SUCCESS, toCartVo(cart, goods), "修改购物车数量成功");
    }

    @Override
    public Result deleteCart(Long goodsId) {
        Long userId = userContext.getUserId();
        LambdaQueryWrapper<UserCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCart::getGoodsId, goodsId)
                .eq(UserCart::getUserId, userId);
        if (cartDao.delete(wrapper) == 0) {
            throw new BusinessException("购物车记录不存在");
        }
        return new Result(Code.SUCCESS, null, "删除购物车成功");
    }

    private UserCart findCart(Long userId, Long goodsId) {
        LambdaQueryWrapper<UserCart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserCart::getGoodsId, goodsId)
                .eq(UserCart::getUserId, userId);
        return cartDao.selectOne(wrapper);
    }

    private Goods requireAvailableGoods(Long goodsId) {
        Goods goods = goodsDao.selectById(goodsId);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }
        if (!Integer.valueOf(1).equals(goods.getStatus())) {
            throw new BusinessException("商品已下架");
        }
        return goods;
    }

    private CartVo toCartVo(UserCart cart, Goods goods) {
        CartVo vo = new CartVo();
        vo.setCartId(cart.getId());
        vo.setGoodsId(cart.getGoodsId());
        vo.setBuyNum(cart.getBuyNum());
        vo.setCreateTime(cart.getCreateTime());
        if (goods == null) {
            vo.setGoodsName("商品已删除");
            vo.setPrice(BigDecimal.ZERO);
            vo.setStock(0);
            vo.setStatus(0);
            vo.setSubtotal(BigDecimal.ZERO);
            return vo;
        }
        vo.setGoodsName(goods.getGoodsName());
        vo.setPicUrl(goods.getPic_url());
        vo.setPrice(goods.getPrice());
        vo.setStock(goods.getStock());
        vo.setStatus(goods.getStatus());
        vo.setSubtotal(goods.getPrice().multiply(BigDecimal.valueOf(cart.getBuyNum())));
        return vo;
    }
}
