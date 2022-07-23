-- 参数列表
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 当前时间
local timestamp = ARGV[3]
-- 订单id
local orderId = ARGV[4]

-- key
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId


-- 获取开始时间
local beginTimestamp = redis.call('HGET', stockKey, 'beginTimestamp')
-- nil在lua中用false标识
if not beginTimestamp or tonumber(timestamp) - tonumber(beginTimestamp) < 0 then
    redis.log(redis.LOG_WARNING, timestamp)
    -- 活动未开始
    return 3
end

-- 判断库存是否充足
if tonumber(redis.call('HGET', stockKey, 'stock')) <= 0 then
    -- 库存不足，返回1
    return 1
end
-- 判读用户是否已经下单
if redis.call('SISMEMBER', orderKey, userId) == 1 then
    -- 已经下单，返回2
    return 2
end

-- 扣减库存
redis.call('HINCRBY', stockKey, 'stock', -1)
-- 保存用户下单
redis.call('SADD', orderKey, userId)
-- 订单信息发送至stream XADD stream.orders * k1 v1 k2 v2 ...
redis.call('XADD', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0

