package com.gauransh.gateway.redis.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gauransh.gateway.redis.exception.RedisStorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonRedisSerializerTest {

    private final JacksonRedisSerializer<TestPojo> serializer = new JacksonRedisSerializer<>();

    static class TestPojo {
        private String name;
        private int capacity;

        public TestPojo() {}

        public TestPojo(String name, int capacity) {
            this.name = name;
            this.capacity = capacity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }

    @Test
    @DisplayName("Should serialize POJO instance to JSON string")
    void testSerialize() {
        TestPojo pojo = new TestPojo("token-bucket", 100);
        String json = serializer.serialize(pojo);

        assertThat(json).contains("\"name\":\"token-bucket\"");
        assertThat(json).contains("\"capacity\":100");
    }

    @Test
    @DisplayName("Should deserialize JSON string back to POJO instance")
    void testDeserialize() {
        String json = "{\"name\":\"sliding-window\",\"capacity\":50}";
        TestPojo pojo = serializer.deserialize(json, TestPojo.class);

        assertThat(pojo).isNotNull();
        assertThat(pojo.getName()).isEqualTo("sliding-window");
        assertThat(pojo.getCapacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should return null when serializing null or deserializing blank")
    void testNullAndBlank() {
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.deserialize(null, TestPojo.class)).isNull();
        assertThat(serializer.deserialize("   ", TestPojo.class)).isNull();
    }

    @Test
    @DisplayName("Should throw RedisStorageException when deserialization fails on invalid JSON")
    void testDeserializationError() {
        String invalidJson = "{invalid-json}";
        assertThatThrownBy(() -> serializer.deserialize(invalidJson, TestPojo.class))
                .isInstanceOf(RedisStorageException.class)
                .hasMessageContaining("Failed to deserialize JSON");
    }
}
