-- key
local key = KEYS[1]
-- 当前的线程标识
local threadId = ARGV[1]
-- 获取redis中的id
local id = redis.call("get",key)
-- 比较是否一致
if(id == threadId) then
    --删除
    return redis.call("del",key)
end
return 0