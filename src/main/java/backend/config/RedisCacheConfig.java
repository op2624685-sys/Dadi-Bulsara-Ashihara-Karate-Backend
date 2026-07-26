package backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration: 10 minutes TTL, serialize keys as String, values as JSON
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()));

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
