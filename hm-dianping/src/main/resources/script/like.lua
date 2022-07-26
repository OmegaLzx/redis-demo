-- 参数定义
-- 当前点赞用户
local userId = ARGV[1]
-- 点赞key
local blogId = ARGV[2]
-- 点赞core（时间戳）
local score = ARGV[3]
local likedKey = 'blog:liked:' .. blogId

-- 业务逻辑
-- 判读当前用户是否已经点赞
if redis.call('ZSCORE', likedKey, userId) then
    -- 已经点赞，则取消点赞
    redis.call('ZREM', likedKey, userId)
    return 1
else
    -- 未点赞， 则增加点赞
    redis.call('ZADD', likedKey, score, userId)
    return 0
end


