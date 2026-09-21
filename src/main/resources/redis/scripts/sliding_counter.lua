-- sliding_counter.lua
--
-- Atomic Sliding Window Counter evaluation.
--
-- The decision depends on two counters read together, a weighted calculation, and a conditional
-- write. Performed as separate commands those steps would interleave between Gateway instances:
-- two instances could each read the same counts, each conclude there is room for one more request,
-- and each increment — admitting more than the configured capacity. Redis runs this script to
-- completion without interruption, so the whole read-calculate-decide-write sequence is one atomic
-- step across every instance.
--
-- Only admitted requests increment the counter. A rejected request must not consume quota, which
-- is why the increment sits inside the branch rather than before it.
--
-- KEYS[1] - current window counter key
-- KEYS[2] - previous window counter key
-- ARGV[1] - capacity (integer, > 0)
-- ARGV[2] - previous window weight in [0,1]
-- ARGV[3] - ttl in seconds (0 disables expiration management)
--
-- Returns: { allowed, remaining } where allowed is 1 or 0

local capacity = tonumber(ARGV[1])
if capacity == nil or capacity <= 0 then
    return redis.error_reply('sliding_counter.lua: ARGV[1] (capacity) must be a positive number')
end

local weight = tonumber(ARGV[2])
if weight == nil or weight < 0 or weight > 1 then
    return redis.error_reply('sliding_counter.lua: ARGV[2] (weight) must be between 0 and 1')
end

local ttlSeconds = tonumber(ARGV[3])
if ttlSeconds == nil or ttlSeconds < 0 then
    return redis.error_reply('sliding_counter.lua: ARGV[3] (ttlSeconds) must be a non-negative number')
end

-- A missing counter reads as false, which is the normal case for a new client or an expired
-- window; both are simply zero.
local currentRaw = redis.call('GET', KEYS[1])
local current = 0
if currentRaw then
    current = tonumber(currentRaw) or 0
end

local previousRaw = redis.call('GET', KEYS[2])
local previous = 0
if previousRaw then
    previous = tonumber(previousRaw) or 0
end

local estimated = (previous * weight) + current

if estimated + 1 > capacity then
    return { 0, 0 }
end

local updatedCurrent = redis.call('INCRBY', KEYS[1], 1)

-- The expiration is set only when the key has none, so repeated requests inside one window do not
-- extend its lifetime. The counter must outlive its own window because the next window reads it as
-- the previous window.
if ttlSeconds > 0 and redis.call('PTTL', KEYS[1]) < 0 then
    redis.call('EXPIRE', KEYS[1], ttlSeconds)
end

local estimatedAfter = (previous * weight) + updatedCurrent
local remaining = math.floor(capacity - estimatedAfter)
if remaining < 0 then
    remaining = 0
end

return { 1, remaining }
