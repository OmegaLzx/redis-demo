-- KEYS[1] 锁的key
-- ARGV[1] 期望的线程标识
-- 如果一致则释放锁
if redis.call('GET', KEYS[1]) == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0