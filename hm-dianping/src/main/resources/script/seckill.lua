-- 参数列表
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

-- key
-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存是否充足
if tonumber(redis.call('GET', stockKey)) <= 0 then
    -- 库存不足，返回1
    return 1
end
-- 判读用户是否已经下单
if redis.call('SISMEMBER', orderKey, userId) == 1 then
    -- 已经下单，返回2
    return 2
end

-- 扣减库存
redis.call('INCRBY', stockKey, -1)
-- 保存用户下单
redis.call('SADD', orderKey, userId)
return 0

