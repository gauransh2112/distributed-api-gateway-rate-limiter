-- token_bucket.lua
--
-- Atomic Token Bucket evaluation over a Redis hash holding one client's bucket.
--
-- The decision reads the stored bucket, refills it for the time elapsed since it was last touched,
-- consumes a token when one is available, and writes the result back. Performed as separate
-- commands those steps would interleave between Gateway instances: two instances could each read
-- the same token count, each compute the same post-consumption value, and each write it, so two
-- requests would consume a single token. Redis runs this script to completion without
-- interruption, making the whole sequence one atomic step across every instance.
--
-- Unlike the window algorithms, the new value cannot be expressed as an INCRBY: it depends on
-- elapsed time and is capped at capacity, so the arithmetic has to happen where the state lives.
--
-- State is written on BOTH paths. A rejected request still materialises the tokens accrued up to
-- now and still advances lastRefill, matching the in-memory implementation. Withholding the write
-- on rejection would re-accrue the same interval on the following request.
--
-- Capacity is NOT stored. It is configuration rather than bucket state, resolved per request from
-- the policy snapshot, so persisting it would let a stale value outlive a configuration change.
--
-- Tokens are fractional and are returned as a STRING. Redis converts a Lua number to an integer
-- on the way out, truncating any fractional part — including inside a returned table — which would
-- silently discard up to a whole token on every call and compound as the value is written back.
--
-- KEYS[1] - hash holding this client's bucket (fields: tokens, lastRefill)
-- ARGV[1] - now in epoch milliseconds
-- ARGV[2] - capacity (> 0); the bucket never refills above this
-- ARGV[3] - refill rate in tokens per millisecond (> 0)
-- ARGV[4] - ttl in seconds (0 disables expiration management)
--
-- Returns: { allowed, remaining, retryAfterMillis, tokens }
--          allowed is 1 or 0, remaining is the truncated whole tokens left, retryAfterMillis is 0
--          when allowed, and tokens is the exact fractional count as a string

local nowMillis = tonumber(ARGV[1])
if nowMillis == nil then
    return redis.error_reply('token_bucket.lua: ARGV[1] (nowMillis) must be numeric')
end

local capacity = tonumber(ARGV[2])
if capacity == nil or capacity <= 0 then
    return redis.error_reply('token_bucket.lua: ARGV[2] (capacity) must be a positive number')
end

local refillRatePerMilli = tonumber(ARGV[3])
if refillRatePerMilli == nil or refillRatePerMilli <= 0 then
    return redis.error_reply('token_bucket.lua: ARGV[3] (refillRatePerMilli) must be a positive number')
end

local ttlSeconds = tonumber(ARGV[4])
if ttlSeconds == nil or ttlSeconds < 0 then
    return redis.error_reply('token_bucket.lua: ARGV[4] (ttlSeconds) must be a non-negative number')
end

local state = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefill')
local storedTokens = tonumber(state[1])
local storedLastRefill = tonumber(state[2])

local tokens

if storedTokens == nil or storedLastRefill == nil then
    -- No bucket yet, or state that cannot be parsed. A new bucket starts full, which is what lets
    -- a first-time client burst up to capacity.
    tokens = capacity
else
    -- A backward clock yields no refill rather than negative refill, so a skewed instance cannot
    -- drain a bucket shared with the others.
    local elapsedMillis = nowMillis - storedLastRefill
    if elapsedMillis < 0 then
        elapsedMillis = 0
    end

    tokens = storedTokens + (elapsedMillis * refillRatePerMilli)
    if tokens > capacity then
        tokens = capacity
    end
end

local allowed = 0
local retryAfterMillis = 0

if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    retryAfterMillis = math.ceil((1 - tokens) / refillRatePerMilli)
end

-- lastRefill is stored from ARGV[1] verbatim rather than from the parsed number, so the stored
-- timestamp cannot pick up formatting error on the way back out.
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'lastRefill', ARGV[1])

-- Refreshed on every evaluation, not only on admitted requests: a bucket that expired while its
-- client was being throttled would be recreated full, handing back the quota it had just spent.
if ttlSeconds > 0 then
    redis.call('EXPIRE', KEYS[1], ttlSeconds)
end

local remaining = math.floor(tokens)

return { allowed, remaining, retryAfterMillis, tostring(tokens) }
