-- 参数定义
-- 当前点赞用户
local userId = ARGV[1]
-- 点赞key
local blogId = ARGV[2]
local likedKey = 'blog:liked:' .. blogId

-- 业务逻辑
-- 判读当前用户是否已经点赞
if redis.call('SISMEMBER', likedKey, userId) == 1 then
    -- 已经点赞，则取消点赞
    redis.call('SREM', likedKey, userId)
    return 1
else
    -- 未点赞， 则增加点赞
    redis.call('SADD', likedKey, userId)
    return 0
end


