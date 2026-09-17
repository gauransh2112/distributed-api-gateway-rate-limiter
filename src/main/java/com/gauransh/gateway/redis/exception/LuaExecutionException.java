package com.gauransh.gateway.redis.exception;

/**
 * Domain exception raised when a Redis Lua script fails during execution.
 *
 * <p>Maps to error catalog entry {@code REDIS-005} (Lua Script Execution Failed). Covers Lua
 * runtime errors, invalid script arguments, and server-side execution failures.</p>
 *
 * <p>Diagnostics carry the script name only. Internal Lua script bodies must never be exposed
 * through exception messages or logs.</p>
 */
public class LuaExecutionException extends RedisException {

    private static final String ERROR_CODE = "REDIS-005";

    private final String scriptName;

    public LuaExecutionException(String scriptName, String message, Throwable cause) {
        super(ERROR_CODE, String.format("Lua script [%s] execution failed: %s", scriptName, message), cause);
        this.scriptName = scriptName;
    }

    public LuaExecutionException(String scriptName, String message) {
        super(ERROR_CODE, String.format("Lua script [%s] execution failed: %s", scriptName, message));
        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }
}
