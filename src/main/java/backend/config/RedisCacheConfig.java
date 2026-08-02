package backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Spring Boot 4.1 ships Jackson 3 (tools.jackson.databind). The default
        // RedisSerializer.json() in spring-data-redis 4.1 uses the Jackson 3
        // GenericJacksonJsonRedisSerializer WITHOUT default typing — so any
        // @Cacheable read on a fresh cache (or after restart) sees a null target
        // type and throws:
        //   "Deserialization type must not be null; Please provide Object.class
        //    to make use of Jackson3 default typing."
        // This breaks every @Cacheable read (login -> /me -> users::email fails
        // -> client thinks user is unauthenticated).
        //
        // Fix: use the Jackson 3 builder path that Spring Data Redis 4.1 ships
        // specifically for this case, then customize() the underlying
        // JsonMapper.Builder to install our OWN default typing. We cannot use
        // Spring's enableDefaultTyping() helper because it hard-codes
        // DefaultTyping.NON_FINAL (visible in its bytecode), and our cached
        // DTOs are all Java `record`s — records are implicitly final, so
        // NON_FINAL skips them on the writer side, leaving stored JSON without
        // a type id. On the reader side the deserializer then falls back to
        // WRAPPER_ARRAY format and explodes with "Unexpected token
        // START_OBJECT, expected VALUE_STRING" (AsArrayTypeDeserializer failure).
        //
        // Jackson 3 added DefaultTyping.NON_FINAL_AND_RECORDS specifically for
        // this case, which is what we wire up here via the customize() callback.
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        RedisSerializer<Object> jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .customize(builder -> builder.activateDefaultTyping(
                        typeValidator,
                        DefaultTyping.NON_FINAL_AND_RECORDS,
                        JsonTypeInfo.As.PROPERTY))
                .build();

        // Default configuration: 10 minutes TTL, serialize keys as String, values as JSON
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // Custom TTL configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Cosmetics catalog changes very rarely, so we set a longer TTL (1 hour)
        cacheConfigurations.put("cosmetics", defaultConfig.entryTtl(Duration.ofHours(1)));

        // Admin stats can be cached for a shorter duration (5 minutes)
        cacheConfigurations.put("adminStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // Students and Teachers public directories
        cacheConfigurations.put("studentsList", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("teachersList", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // Student and Teacher detail views (15 minutes)
        cacheConfigurations.put("students", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("studentsPublic", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("studentsAdmin", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        cacheConfigurations.put("teachers", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("teachersAdmin", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // User profile and listings (15 minutes)
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("usersList", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}