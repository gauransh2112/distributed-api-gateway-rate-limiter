package com.gauransh.gateway.redis.script;

import java.util.Objects;

/**
 * Immutable representation of a Lua script registered with Redis.
 *
 * <p>Holds the script's logical name, its body as read from the classpath, and the SHA1 digest
 * used for {@code EVALSHA} execution. The digest is the Redis script cache identifier: Redis
 * derives it from the script body, so a locally computed digest and the digest returned by
 * {@code SCRIPT LOAD} are always identical for the same body.</p>
 */
public final class LuaScript {

    private final String name;
    private final String body;
    private final String sha;

    public LuaScript(String name, String body, String sha) {
        this.name = Objects.requireNonNull(name, "Lua script name must not be null");
        this.body = Objects.requireNonNull(body, "Lua script body must not be null");
        this.sha = Objects.requireNonNull(sha, "Lua script SHA must not be null");
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public String getSha() {
        return sha;
    }

    /**
     * Returns a diagnostic representation containing the script name and SHA only.
     *
     * <p>The script body is deliberately excluded: internal Lua scripts must never reach logs.</p>
     */
    @Override
    public String toString() {
        return String.format("LuaScript[name=%s, sha=%s]", name, sha);
    }
}
