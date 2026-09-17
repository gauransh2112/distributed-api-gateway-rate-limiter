-- increment.lua
--
-- Infrastructure-level atomic counter primitive.
--
-- Performs "increment a counter and establish its expiration" as a single
-- atomic state transition. Redis guarantees that no other client observes
-- intermediate state while this script executes, which makes the pair of
-- operations safe across multiple Gateway instances.
--
-- The TTL is applied only when the key currently has no expiration, so an
-- existing expiration window is never extended by subsequent increments.
--
-- KEYS[1] - counter key
-- ARGV[1] - increment amount (integer)
-- ARGV[2] - ttl in seconds (0 disables expiration management)
--
-- Returns: the counter value after incrementing (integer)

local amount = tonumber(ARGV[1])
if amount == nil then
    return redis.error_reply('increment.lua: ARGV[1] (amount) must be numeric')
end

local ttlSeconds = tonumber(ARGV[2])
if ttlSeconds == nil or ttlSeconds < 0 then
    return redis.error_reply('increment.lua: ARGV[2] (ttlSeconds) must be a non-negative number')
end

local value = redis.call('INCRBY', KEYS[1], amount)

if ttlSeconds > 0 and redis.call('PTTL', KEYS[1]) < 0 then
    redis.call('EXPIRE', KEYS[1], ttlSeconds)
end

return value
