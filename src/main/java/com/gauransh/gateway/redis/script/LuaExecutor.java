package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;

import java.util.List;

/**
 * Atomic Lua execution contract for the Redis module.
 *
 * <p>A single Redis command is atomic on its own, but a multi-step state transition
 * (read &rarr; calculate &rarr; update &rarr; refresh TTL &rarr; return) is not. Redis executes an entire
 * Lua script without interruption, so routing such workflows through this contract eliminates
 * race conditions between Gateway instances without distributed locks and without JVM
 * synchronization.</p>
 *
 * <p>Scripts execute through {@code EVALSHA} against a cached SHA digest. A Redis-side script
 * cache miss is recovered automatically by re-registering the script and retrying once.</p>
 *
 * <p>Lua execution belongs exclusively to the Redis module. Rate limiting algorithms never
 * execute scripts directly.</p>
 */
public interface LuaExecutor {

    /**
     * Executes a registered Lua script atomically and returns its result.
     *
     * <p>{@code keys} and {@code args} map onto the script's {@code KEYS} and {@code ARGV} tables.
     * Scripts depend only on these inputs, never on JVM state.</p>
     *
     * <p>{@code resultType} declares how the Redis reply should be decoded, which the Redis
     * protocol requires to be known before the reply is read. Use {@link Long} for integer
     * replies, {@link Boolean} for boolean replies, {@link List} for multi-bulk replies, and
     * {@link String} for status or bulk-string replies.</p>
     *
     * @param scriptName the logical script name (for example {@code increment.lua})
     * @param resultType the expected reply type
     * @param keys       the Redis keys the script operates on
     * @param args       the script arguments
     * @param <T>        the reply type
     * @return the script result, decoded as {@code resultType}
     * @throws LuaScriptNotLoadedException if the script is unknown or cannot be registered with Redis
     * @throws LuaExecutionException       if the script fails during execution
     */
    <T> T executeLua(String scriptName, Class<T> resultType, List<String> keys, List<String> args);
}
