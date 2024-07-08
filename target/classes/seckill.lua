-- 1. 秒杀券id
local voucherId = ARGV[1]
-- 2.用户id
local userId = ARGV[2]
-- 3. 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 4. 订单key
local orderKey = 'seckill:order:' .. voucherId
-- 5. 查询库存
if(tonumber(redis.call('get',stockKey)) <= 0) then
    -- 库存不足
    return 1
end
-- 6. 判断是否重复下单
if(redis.call('sismember',orderKey,userId) == 1) then
    -- 重复下单
    return 2
end
--7. 减少库存
redis.call('incrby',stockKey,-1)
--8. 添加到订单队列
redis.call('sadd',orderKey,userId)
return 0