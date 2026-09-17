package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production implementation of {@link LuaExecutor} executing scripts through {@code EVALSHA}.
 *
 * <p>Execution flow:</p>
 * <pre>
 * EVALSHA (cached SHA)
 *      &darr;
 * NOSCRIPT?  &rarr;  reload script  &rarr;  update SHA  &rarr;  retry once
 *      &darr;
 * Result
 * </pre>
 *
 * <p>{@code EVALSHA} is used instead of {@code EVAL} so the script body is not transmitted on every
 * request. Recovery from a Redis-side script cache miss is automatic and bounded to a single retry,
 * so a persistently failing script cannot cause an unbounded retry loop.</p>
 *
 * <p>This class translates infrastructure failures into the Redis module's exception hierarchy.
 * It never decides HTTP behaviour, retry policy, or fail-open / fail-closed semantics: those
 * belong to the calling module.</p>
 */
public class DefaultLuaExecutor implements LuaExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultLuaExecutor.class);
    private static final String NO_SCRIPT_MARKER = "NOSCRIPT";

    private final StringRedisTemplate redisTemplate;
    private final LuaScriptLoader scriptLoader;

    public DefaultLuaExecutor(StringRedisTemplate redisTemplate, LuaScriptLoader scriptLoader) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null");
        this.scriptLoader = Objects.requireNonNull(scriptLoader, "LuaScriptLoader must not be null");
    }

    @Override
    public <T> T executeLua(String scriptName, Class<T> resultType, List<String> keys, List<String> args) {
        validateScriptName(scriptName);
        Objects.requireNonNull(resultType, "Lua result type must not be null");
        validateArguments("keys", keys);
        validateArguments("args", args);

        LuaScript script = scriptLoader.getScript(scriptName);

        try {
            return evalSha(script, resultType, keys, args);
        } catch (LuaScriptNotLoadedException e) {
            throw e;
        } catch (Exception e) {
            if (!isScriptCacheMiss(e)) {
                throw new LuaExecutionException(scriptName, describe(e), e);
            }
            log.debug("Redis script cache miss for Lua script [{}], reloading and retrying once", scriptName);
            LuaScript reloaded = scriptLoader.reload(scriptName);
            try {
                return evalSha(reloaded, resultType, keys, args);
            } catch (Exception retryFailure) {
                throw new LuaExecutionException(scriptName,
                        "retry after script reload failed: " + describe(retryFailure), retryFailure);
            }
        }
    }

    private <T> T evalSha(LuaScript script, Class<T> resultType, List<String> keys, List<String> args) {
        byte[][] keysAndArgs = toByteArguments(keys, args);
        ReturnType returnType = ReturnType.fromJavaType(resultType);
        return redisTemplate.execute((RedisCallback<T>) connection ->
                connection.scriptingCommands().evalSha(script.getSha(), returnType, keys.size(), keysAndArgs));
    }

    private byte[][] toByteArguments(List<String> keys, List<String> args) {
        List<byte[]> arguments = new ArrayList<>(keys.size() + args.size());
        keys.forEach(key -> arguments.add(key.getBytes(StandardCharsets.UTF_8)));
        args.forEach(arg -> arguments.add(arg.getBytes(StandardCharsets.UTF_8)));
        return arguments.toArray(new byte[0][]);
    }

    /**
     * Detects a Redis script cache miss.
     *
     * <p>Redis reports a missing script as a {@code NOSCRIPT} error, which the driver surfaces as a
     * dedicated exception type wrapped in a Spring translation exception. Both the exception type
     * name and the message are inspected across the cause chain so detection does not depend on a
     * specific driver's internal class.</p>
     */
    private boolean isScriptCacheMiss(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getClass().getSimpleName().contains("NoScript")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(NO_SCRIPT_MARKER)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }

    private void validateScriptName(String scriptName) {
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalArgumentException("Lua script name must not be null or blank");
        }
    }

    private void validateArguments(String parameter, List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException(String.format("Lua script %s must not be null", parameter));
        }
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(String.format("Lua script %s must not contain null values", parameter));
        }
    }
}
