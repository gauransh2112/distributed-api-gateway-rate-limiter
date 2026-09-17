package com.gauransh.gateway.redis.exception;

/**
 * Domain exception raised when a requested Lua script is unavailable to the execution engine.
 *
 * <p>Maps to error catalog entry {@code REDIS-006} (Lua Script Not Loaded). Raised when a script
 * name was never registered with the loader, or when the script body could not be re-registered
 * with Redis during cache-miss recovery.</p>
 */
public class LuaScriptNotLoadedException extends RedisException {

    private static final String ERROR_CODE = "REDIS-006";

    private final String scriptName;

    public LuaScriptNotLoadedException(String scriptName, String message) {
        super(ERROR_CODE, String.format("Lua script [%s] is not loaded: %s", scriptName, message));
        this.scriptName = scriptName;
    }

    public LuaScriptNotLoadedException(String scriptName, String message, Throwable cause) {
        super(ERROR_CODE, String.format("Lua script [%s] is not loaded: %s", scriptName, message), cause);
        this.scriptName = scriptName;
    }

    public String getScriptName() {
        return scriptName;
    }
}
