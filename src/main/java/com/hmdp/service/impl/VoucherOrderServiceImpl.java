package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIWorker redisIWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> redisScript;
    static {
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("seckill.lua"));
        redisScript.setResultType(Long.class);
    }
    private final IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
    private final BlockingQueue<VoucherOrder> queue = new LinkedBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    @PostConstruct
    public void init() {
        SECKILL_EXECUTOR_SERVICE.submit((Runnable) () -> {
            while (true) {
                try {
                    VoucherOrder voucherOrder = queue.take();
                    handleVoucherOrderAsync(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("数据库操作错误");
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void handleVoucherOrderAsync(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        if (userId == null || voucherId == null) {
            return;
        }
        long count = proxy.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            // 如果用户已经下单，无法再次下单
            return;
        }
        // 使用CAS锁进防止库存超卖
        boolean success = proxy.update()
                .setSql("stock = stock - 1")
                .gt("stock", 0)
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            // 库存不足
            return;
        }
        // 下单成功扣减库存
        proxy.save(voucherOrder);
        return;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null || voucherId <= 0) {
            return Result.fail("优惠券ID不存在");
        }
        // 执行lua脚本判断是否满足下单资格
        long userId = UserHolder.getUser().getId();
        long res = stringRedisTemplate.execute(redisScript, Collections.EMPTY_LIST, voucherId, userId);
        if (res == 1) {
            return Result.fail("库存不足");
        }
        if (res == 2) {
            return Result.fail("用户已下单");
        }
        if (res != 0) {
            return Result.fail("未知错误");
        }
        // 用户触发优惠券秒杀，将用户订单信息加入到阻塞队列中
        long id = redisIWorker.nextId("order");
        VoucherOrder order = VoucherOrder.builder()
                .id(id)
                .userId(userId)
                .voucherId(voucherId)
                .build();
        queue.add(order);
        return Result.ok(voucherId);
    }

    /**
     * @param voucherId
     * @return
     */
    /*
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 判断id是否有效
        if (voucherId == null || voucherId <= 0) {
            return Result.fail("优惠券ID无效");
        }
        // 查询秒杀券是否在数据库中
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("库中不存在该优惠券");
        }
        // 查看是否处于秒杀时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now()) || seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("当前不是秒杀时间");
        }
        // 查看库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 使用分布式锁解决超买问题
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        try {
            boolean isLock = lock.tryLock(2, TimeUnit.SECONDS);
            if(!isLock) {
                return Result.fail("不允许重复下单");
            }
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createSeckillVoucher(voucherId);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

     */

    @Transactional
    public Result createSeckillVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        // 使用悲观锁解决用户超买
        Long count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("同一用户只能购买一次!");
        }
        // 使用CAS解决库存超卖问题
        // 库存减少
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 只要库存仍然大于0, 那么处于这个语句的线程就能继续执行
                .update();
        if (!success) {
            return Result.fail("扣减失败");
        }
        long orderId = redisIWorker.nextId(RedisConstants.SECKILL_STOCK_KEY + voucherId);
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(UserHolder.getUser().getId())
                .voucherId(voucherId)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();
        // 返回结果
        save(voucherOrder);
        return Result.ok("秒杀成功");
    }

}
