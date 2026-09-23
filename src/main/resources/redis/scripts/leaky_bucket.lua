-- leaky_bucket.lua
--
-- Atomic Leaky Bucket evaluation over a Redis hash holding one client's bucket.
--
-- The decision reads the stored water level, drains it for the time elapsed since the bucket was
-- last touched, and admits the request only if there is room for one more unit. Performed as
-- separate commands those steps would interleave between Gateway instances: two instances could
-- each read the same level, each find room for one more unit, and each write, admitting past
-- capacity. Redis runs this script to completion without interruption, so the whole sequence is
-- one atomic step across every instance.
--
-- As with Token Bucket the new level depends on elapsed time and is clamped, so it cannot be
-- expressed as an INCRBY: the arithmetic has to happen where the state lives.
--
-- STATE MODEL. The Redis design describes this algorithm as a List used as a FIFO queue of
-- requests. The Gateway's in-memory LeakyBucketRateLimiter is not a queue: it is a meter holding
-- one fractional water level that rises by one unit per admitted request and drains continuously.
-- A List cannot hold that level alongside the last-leak timestamp the same design requires, and
-- the in-memory implementation is the behavioural reference the distributed version must match, so
-- the state is a hash of { level, lastLeak }. This deviation is recorded in the Redis setup guide.
--
-- CLOCK REGRESSION. Unlike Token Bucket, lastLeak must NEVER move backwards -- the in-memory
-- implementation states this as an explicit invariant. A backward clock yields no leak and leaves
-- the stored timestamp untouched, so a skewed instance cannot rewind a bucket shared with others.
--
-- State is written on BOTH paths. A rejected request still materialises the water drained up to
-- now, matching the in-memory implementation; withholding the write would re-drain the same
-- interval on the following request.
--
-- KEYS[1] - hash holding this client's bucket (fields: level, lastLeak)
-- ARGV[1] - now in epoch milliseconds
-- ARGV[2] - capacity (> 0); the level may never exceed this
-- ARGV[3] - leak rate in units per millisecond (> 0)
-- ARGV[4] - ttl in seconds (0 disables expiration management)
--
-- Returns: { allowed, level, lastLeak }
--          allowed is 1 or 0; level is the exact fractional level AFTER this evaluation, returned
--          as a string because Redis truncates a Lua number to an integer on the way out; lastLeak
--          is the timestamp actually stored, which is not necessarily ARGV[1] under a backward
--          clock. Everything the client is told -- remaining, retryAfter, resetTime -- is derived
--          from these three values on the Java side, where it can mirror the in-memory rounding
--          rules exactly.

local nowMillis = tonumber(ARGV[1])
if nowMillis == nil then
    return redis.error_reply('leaky_bucket.lua: ARGV[1] (nowMillis) must be numeric')
end

local capacity = tonumber(ARGV[2])
if capacity == nil or capacity <= 0 then
    return redis.error_reply('leaky_bucket.lua: ARGV[2] (capacity) must be a positive number')
end

local leakRatePerMilli = tonumber(ARGV[3])
if leakRatePerMilli == nil or leakRatePerMilli <= 0 then
    return redis.error_reply('leaky_bucket.lua: ARGV[3] (leakRatePerMilli) must be a positive number')
end

local ttlSeconds = tonumber(ARGV[4])
if ttlSeconds == nil or ttlSeconds < 0 then
    return redis.error_reply('leaky_bucket.lua: ARGV[4] (ttlSeconds) must be a non-negative number')
end

local state = redis.call('HMGET', KEYS[1], 'level', 'lastLeak')
local storedLevel = tonumber(state[1])
local storedLastLeak = tonumber(state[2])

local level
local lastLeak

if storedLevel == nil or storedLastLeak == nil then
    -- No bucket yet, or state that cannot be parsed. A new bucket starts EMPTY, which is the
    -- opposite of Token Bucket and is why this algorithm grants no initial burst.
    level = 0
    lastLeak = nowMillis
elseif nowMillis < storedLastLeak then
    -- Backward clock: no time has passed, and the stored timestamp is preserved rather than
    -- rewound, so the bucket cannot be made to leak twice for the same interval later.
    level = storedLevel
    lastLeak = storedLastLeak
else
    local elapsedMillis = nowMillis - storedLastLeak
    level = storedLevel - (elapsedMillis * leakRatePerMilli)
    if level < 0 then
        level = 0
    end
    lastLeak = nowMillis
end

local allowed = 0

if level + 1 <= capacity then
    level = level + 1
    allowed = 1
end

redis.call('HSET', KEYS[1], 'level', tostring(level), 'lastLeak', tostring(lastLeak))

-- Refreshed on every evaluation, not only on admitted requests: a bucket that expired while its
-- client was being throttled would be recreated empty, handing back the capacity it had just used.
if ttlSeconds > 0 then
    redis.call('EXPIRE', KEYS[1], ttlSeconds)
end

return { allowed, tostring(level), lastLeak }
