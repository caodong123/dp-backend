package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
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

    @Resource
    private RedissonClient redissonClient;

    public static DefaultRedisScript SECKKILL_SCRIPT;
    //代理对象  解决线程不同的情况
    private IVoucherOrderService proxy;

    //使用阻塞队列
    private BlockingDeque<VoucherOrder> orderQueue = new LinkedBlockingDeque<>(1024 * 1024);
    //线程常量池
    private static final ExecutorService es = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        //初始化时就一直监听阻塞队列
        es.submit(new VoucherOrderHandler());
    }

    class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder order = orderQueue.take();
                    // 2.创建订单
                    handleVoucherOrder(order);
                } catch (InterruptedException e) {
                    log.error("处理订单异常");
                }
            }
        }
    }

    public void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        //获取锁对象
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order" + voucherOrder.getVoucherId());
        // 获取锁
        Boolean isLocked = lock.tryLock(1,10, TimeUnit.SECONDS);
        //获取失败
        if(!isLocked){
            log.error("获取锁失败");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }


    static {
        SECKKILL_SCRIPT = new DefaultRedisScript<>();
        SECKKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKKILL_SCRIPT.setResultType(Long.class);
    }


    // 秒杀优惠券 返回订单id
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        Long userId = UserHolder.getUser().getId();
        //1. 在redis中查询是否有购买资格
        Long res = (Long) stringRedisTemplate.execute(
                SECKKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //返回值不是0 不能购买
        if (res.intValue() != 0) {
            return Result.fail(res.intValue() == 1 ? "库存不足" : "不能重复购买");
        }
        // todo 将订单信息放入消息队列
        // 7 保存订单
        VoucherOrder order = new VoucherOrder();
        // 7.1 生成订单id
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        // 7.2 设置用户id
        order.setUserId(UserHolder.getUser().getId());
        // 7.3 优惠券id
        order.setVoucherId(voucherId);
        //放入阻塞队列
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        orderQueue.add(order);
        // ================

        return Result.ok(orderId);
    }


    /*// 秒杀优惠券  使用乐观锁
    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
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
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order" + id);
        // 获取锁
        Boolean isLocked = lock.tryLock(1,10, TimeUnit.SECONDS);
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
            lock.unlock();
        }
    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5判断用户是否已经购买过
        Long userId = UserHolder.getUser().getId();
        //加锁
        synchronized (userId.toString().intern()) {
            int count = query().eq("user_id", userId)
                    .eq("voucher_id", voucherOrder)
                    .count();
            if (count > 0) {
                log.error("该用户已经购买过!");
            }
            // 6 扣减库存
            boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder)
                    .gt("stock", 0).update();   //where id = voucherId and stock > 0
            if (!success) {
                log.error("库存不足");
            }
            save(voucherOrder);
        }
    }
}
