package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis中查询商铺类型缓存
        List<String> redisList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2.判断是否存在
        if (!redisList.isEmpty()){
            // 3.存在，从字符串列表还原为ShopType对象列表，然后返回
            List<ShopType> shopTypes = redisList.stream()
                    .map(jsonStr -> JSONUtil.toBean(jsonStr, ShopType.class) )
                    .collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        // 4.不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 5.不存在，返回错误
        if (typeList == null || typeList.isEmpty()){
            return Result.fail("分类不存在");
        }
        // 6.存在，转换为字符串列表并写入redis
        List<String> list = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,list);

        return Result.ok(typeList);

    }
}
