package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2 不符合 返回
            return Result.fail("手机号格式错误");
        }
        // 3 符合 生成六位验证码
        String code = RandomUtil.randomNumbers(6);
        // 4 保存到redis  并设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5 发送验证码
        log.debug("发送验证码，code={}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1 校验手机号
        String phone=loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 不符合 返回
            return Result.fail("手机号格式错误");
        }
        // 2 校验验证码
        String code = loginForm.getCode();
        //从redis中取出验证码进行对比
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(!code.equals(cacheCode)){
            //报错
            return Result.fail("验证码错误");
        }
        // 3 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        // 4 判断用户是否存在
        if(user==null ){
            user= createUser(phone);
        }
        // 5 保存到redis
        // 5.1 生成token
        String token = UUID.randomUUID().toString(true);
        // 5.2 生成dto减少内存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 5.3  产生key 和value
        String tokenKey = LOGIN_USER_KEY+token;
        Map<String, Object> beanMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().ignoreNullValue()
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(tokenKey,beanMap);
        // 5.4 设置有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL,TimeUnit.SECONDS);
        //把token返回给前端
        return Result.ok(token);
    }

    private User createUser(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        //保存到数据库
        save(user);
        return user;
    }
}
