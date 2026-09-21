-- sliding_log.lua
--
-- Atomic Sliding Window Log evaluation over a sorted set of request timestamps.
--
-- The decision evicts expired entries, counts what remains, and inserts only if the count is below
-- capacity. Performed as separate commands those steps would interleave between Gateway instances:
-- two instances could each count the same sub-capacity total and each insert, admitting more than
-- capacity. Redis runs this script to completion without interruption, so the whole sequence is one
-- atomic step across every instance.
--
-- Eviction uses an exclusive upper bound, so an entry scored exactly at the cutoff
-- (now - windowDuration) remains active and one millisecond older is expired. This matches the
-- in-memory algorithm's boundary semantics exactly.
--
-- The member carries a caller-supplied unique token rather than being the bare timestamp. Sorted
-- set members are unique, so two requests arriving in the same millisecond would otherwise collide
-- on one member: ZADD would update that member's score instead of adding a second entry, and the
-- second request would go uncounted. That under-count is invisible to sequential tests and appears
-- only under concurrency, which is precisely the failure this algorithm exists to prevent.
--
-- KEYS[1] - sorted set holding this client's request log
-- ARGV[1] - now in epoch milliseconds (the score for a new entry)
-- ARGV[2] - cutoff in epoch milliseconds; entries scored below this are expired
-- ARGV[3] - capacity (integer, > 0)
-- ARGV[4] - ttl in seconds (0 disables expiration management)
-- ARGV[5] - unique member token for this request
--
-- Returns: { allowed, remaining, oldestScore } where allowed is 1 or 0 and oldestScore is -1
--          when the log is empty

local nowMillis = tonumber(ARGV[1])
if nowMillis == nil then
    return redis.error_reply('sliding_log.lua: ARGV[1] (nowMillis) must be numeric')
end

local cutoffMillis = tonumber(ARGV[2])
if cutoffMillis == nil then
    return redis.error_reply('sliding_log.lua: ARGV[2] (cutoffMillis) must be numeric')
end

local capacity = tonumber(ARGV[3])
if capacity == nil or capacity <= 0 then
    return redis.error_reply('sliding_log.lua: ARGV[3] (capacity) must be a positive number')
end

local ttlSeconds = tonumber(ARGV[4])
if ttlSeconds == nil or ttlSeconds < 0 then
    return redis.error_reply('sliding_log.lua: ARGV[4] (ttlSeconds) must be a non-negative number')
end

local member = ARGV[5]
if member == nil or member == '' then
    return redis.error_reply('sliding_log.lua: ARGV[5] (member) must not be empty')
end

-- Drop everything that has rolled out of the window. '(' makes the bound exclusive, so an entry
-- scored exactly at the cutoff survives.
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. cutoffMillis)

local count = redis.call('ZCARD', KEYS[1])
local allowed = 0

if count < capacity then
    redis.call('ZADD', KEYS[1], nowMillis, member)
    count = count + 1
    allowed = 1

    -- Refreshed on every admitted request rather than set once: the log is continuous, so the key
    -- should outlive its newest entry and expire only once no active requests remain.
    if ttlSeconds > 0 then
        redis.call('EXPIRE', KEYS[1], ttlSeconds)
    end
end

local oldestScore = -1
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
if oldest and oldest[2] then
    oldestScore = math.floor(tonumber(oldest[2]))
end

local remaining = 0
if allowed == 1 then
    remaining = capacity - count
    if remaining < 0 then
        remaining = 0
    end
end

return { allowed, remaining, oldestScore }
