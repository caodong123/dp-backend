package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    // 秒杀优惠券  使用乐观锁
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1 根据id查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("优惠券不存在");
        }
        // 2 判断活动是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("尚未开始");
        }
        // 3 判断活动是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束");
        }
        // 4 判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        Long id = UserHolder.getUser().getId();
        //获取锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        // 获取锁
        Boolean isLocked = lock.tryLock(10L);
        //获取失败
        if(!isLocked){
            return Result.fail("该用户已经购买过了！");
        }
        try {
            // 获取代理对象  如果不获取的话是使用的this调用createOrder  事务会失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unLock();
        }
    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId) {
        // 5判断用户是否已经购买过
        Long userId = UserHolder.getUser().getId();
        //加锁
        synchronized (userId.toString().intern()){
            int count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (count > 0){
                return Result.fail("该用户已经购买过!");
            }
            // 6 扣减库存
            boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0).update();   //where id = voucherId and stock > 0
            if(!success){
                return Result.fail("库存不足");
            }
            // 7 保存订单
            VoucherOrder order = new VoucherOrder();
            // 7.1 生成订单id
            long orderId = redisIdWorker.nextId("order");
            order.setId(orderId);
            // 7.2 设置用户id
            order.setUserId(UserHolder.getUser().getId());
            // 7.3 优惠券id
            order.setVoucherId(voucherId);
            save(order);
            return Result.ok(orderId);
        }
    }
}
