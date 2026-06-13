-- 使用lua脚本完成秒杀判断和一人一单的业务
-- 传入的参数
local voucherId = argv[1]
local userId = argv[2]
local orderId = argv[3]
local stockKey = "seckill:stock" .. voucherId
local userKey = "seckill:order" .. voucherId
if (tonumber(redis.call("get", stockKey) < 1)) then
    -- 库存不足
    return 1
end
if (redis.call("ismember", userKey, userId)) then
    -- 用户已经下单
    return 2
end
-- 秒杀成功，扣减库存，添加用户
redis.call("incrby", stockKey, -1)
redis.call("sadd", userKey, userId);
-- 将订单信息加入到stream消息队列
redis.call("xadd", "stream.orders", "*", "userId", userId, "orderId", orderId ,"voucherId", voucherId)
return 0