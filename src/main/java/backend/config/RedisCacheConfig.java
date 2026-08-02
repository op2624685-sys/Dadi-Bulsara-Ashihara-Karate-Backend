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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Build a Jackson 3 ObjectMapper with default typing configured for our
     * cache value types. Spring Boot 4.1 ships Jackson 3 (tools.jackson.databind).
     *
     * The default RedisSerializer.json() in spring-data-redis 4.1 ships the
     * Jackson 3 GenericJacksonJsonRedisSerializer WITHOUT default typing — any
     * @Cacheable read on a fresh cache throws:
     *   "Deserialization type must not be null; Please provide Object.class to
     *    make use of Jackson3 default typing."
     *
     * Spring's builder.enableDefaultTyping(validator) helper hardcodes
     * DefaultTyping.NON_FINAL + As.WRAPPER_ARRAY (visible in bytecode). Our
     * cached DTOs are all Java records — records are implicitly final, so
     * NON_FINAL skips them and stored JSON ends up without a type id; the
     * reader then explodes trying to locate a wrapper-array type id on an
     * object.
     *
     * Jackson 3 added DefaultTyping.NON_FINAL_AND_RECORDS specifically for this
     * case, paired with As.PROPERTY which writes the type id as a sibling
     * property ({ "@class": "...", ... }) and is symmetric on read. We build
     * the ObjectMapper ourselves and pass it via the constructor — the
     * GenericJacksonJsonRedisSerializer constructor takes a configured
     * ObjectMapper directly without applying its own default-typing config.
     */
    private ObjectMapper buildCacheMapper() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        return JsonMapper.builder()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY)
                .build();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> jsonSerializer = new GenericJacksonJsonRedisSerializer(buildCacheMapper());

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