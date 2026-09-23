package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Lua scripts from the classpath, registers them with Redis, and caches their SHA digests.
 *
 * <p>Script lifecycle:</p>
 * <pre>
 * Application Startup
 *         &darr;
 * Read Script From Classpath
 *         &darr;
 * Compute SHA1
 *         &darr;
 * SCRIPT LOAD
 *         &darr;
 * Cache SHA  &rarr;  EVALSHA on every subsequent execution
 * </pre>
 *
 * <p>Reading a script body is a local, deterministic operation and a missing script resource is a
 * packaging defect, so it fails fast. Registering a script with Redis is a network operation:
 * failure at startup is logged and tolerated, because the execution engine recovers automatically
 * from a Redis-side script cache miss ({@code NOSCRIPT}). This preserves the established behaviour
 * that Redis unavailability never prevents the Gateway from starting.</p>
 *
 * <p>This class is an internal component of the Redis module. Business modules interact with Lua
 * exclusively through {@link LuaExecutor}.</p>
 */
public class LuaScriptLoader {

    /** Logical name of the infrastructure-level atomic counter script. */
    public static final String INCREMENT_SCRIPT = "increment.lua";

    /** Logical name of the Sliding Window Counter evaluation script. */
    public static final String SLIDING_COUNTER_SCRIPT = "sliding_counter.lua";

    /** Logical name of the Sliding Window Log evaluation script. */
    public static final String SLIDING_LOG_SCRIPT = "sliding_log.lua";

    /** Logical name of the Token Bucket evaluation script. */
    public static final String TOKEN_BUCKET_SCRIPT = "token_bucket.lua";

    /** Logical name of the Leaky Bucket evaluation script. */
    public static final String LEAKY_BUCKET_SCRIPT = "leaky_bucket.lua";

    /** Classpath location owning every Lua script shipped with the Gateway. */
    public static final String SCRIPT_BASE_PATH = "redis/scripts/";

    private static final Logger log = LoggerFactory.getLogger(LuaScriptLoader.class);
    private static final String SHA1 = "SHA-1";

    private final StringRedisTemplate redisTemplate;
    private final List<String> scriptNames;
    private final Map<String, LuaScript> scriptCache = new ConcurrentHashMap<>();

    public LuaScriptLoader(StringRedisTemplate redisTemplate, List<String> scriptNames) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null");
        Objects.requireNonNull(scriptNames, "Lua script names must not be null");
        if (scriptNames.isEmpty()) {
            throw new IllegalArgumentException("At least one Lua script name must be provided");
        }
        this.scriptNames = List.copyOf(scriptNames);
    }

    /**
     * Reads every configured script from the classpath and registers it with Redis.
     *
     * <p>Invoked once during application startup.</p>
     *
     * @throws LuaScriptNotLoadedException if a script resource is missing or unreadable
     */
    public void initialize() {
        for (String scriptName : scriptNames) {
            String body = readScriptBody(scriptName);
            LuaScript script = new LuaScript(scriptName, body, computeSha(scriptName, body));
            scriptCache.put(scriptName, script);
            registerWithRedis(script);
        }
        log.info("Lua script loader initialized with {} script(s)", scriptCache.size());
    }

    /**
     * Returns a previously loaded script.
     *
     * @param scriptName the logical script name (for example {@code increment.lua})
     * @return the cached script including its SHA digest
     * @throws LuaScriptNotLoadedException if the script was never loaded
     */
    public LuaScript getScript(String scriptName) {
        validateScriptName(scriptName);
        LuaScript script = scriptCache.get(scriptName);
        if (script == null) {
            throw new LuaScriptNotLoadedException(scriptName, "script is not registered with the Lua script loader");
        }
        return script;
    }

    /**
     * Re-registers a cached script with Redis and refreshes its SHA digest.
     *
     * <p>Used by the execution engine to recover from a Redis-side script cache miss.</p>
     *
     * @param scriptName the logical script name
     * @return the script with a refreshed SHA digest
     * @throws LuaScriptNotLoadedException if the script is unknown or Redis rejects the registration
     */
    public LuaScript reload(String scriptName) {
        LuaScript cached = getScript(scriptName);
        String sha;
        try {
            sha = scriptLoad(cached.getBody());
        } catch (Exception e) {
            throw new LuaScriptNotLoadedException(scriptName, "Redis rejected script registration: " + e.getMessage(), e);
        }
        if (sha == null) {
            throw new LuaScriptNotLoadedException(scriptName, "Redis returned no SHA for the registered script");
        }
        LuaScript reloaded = new LuaScript(scriptName, cached.getBody(), sha);
        scriptCache.put(scriptName, reloaded);
        log.debug("Reloaded Lua script into the Redis script cache: {}", reloaded);
        return reloaded;
    }

    /**
     * Returns the logical names of every script this loader manages.
     */
    public List<String> getScriptNames() {
        return scriptNames;
    }

    private void registerWithRedis(LuaScript script) {
        try {
            String sha = scriptLoad(script.getBody());
            if (sha != null && !sha.equals(script.getSha())) {
                scriptCache.put(script.getName(), new LuaScript(script.getName(), script.getBody(), sha));
            }
            log.debug("Registered Lua script with Redis: {}", script);
        } catch (Exception e) {
            // Redis unavailability must never prevent startup. The execution engine reloads the
            // script automatically on the first NOSCRIPT response.
            log.warn("Could not register Lua script [{}] with Redis during startup, "
                    + "it will be registered on first execution: {}", script.getName(), e.getMessage());
        }
    }

    private String scriptLoad(String body) {
        return redisTemplate.execute(
                (RedisCallback<String>) connection ->
                        connection.scriptingCommands().scriptLoad(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String readScriptBody(String scriptName) {
        validateScriptName(scriptName);
        ClassPathResource resource = new ClassPathResource(SCRIPT_BASE_PATH + scriptName);
        if (!resource.exists()) {
            throw new LuaScriptNotLoadedException(scriptName,
                    "script resource not found on classpath at " + SCRIPT_BASE_PATH + scriptName);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LuaScriptNotLoadedException(scriptName, "script resource could not be read: " + e.getMessage(), e);
        }
    }

    private String computeSha(String scriptName, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA1);
            return HexFormat.of().formatHex(digest.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new LuaScriptNotLoadedException(scriptName, "SHA-1 digest is unavailable on this JVM", e);
        }
    }

    private void validateScriptName(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalArgumentException("Lua script name must not be null or blank");
        }
    }
}
