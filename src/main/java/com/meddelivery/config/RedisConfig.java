package com.meddelivery.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        log.info("Redis password present: {}", (redisPassword != null && !redisPassword.isEmpty()));
        
        // Redis standalone configuration
        RedisStandaloneConfiguration redisConfig = 
                new RedisStandaloneConfiguration(redisHost, redisPort);
        
        // Set password if provided
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
            log.info("Redis password configured");
        }

        // Socket options with longer timeout
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(30))
                .keepAlive(true)
                .build();

        // Client options with auto-reconnect
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .build();

        // Lettuce pool configuration
        LettucePoolingClientConfiguration poolConfig = 
                LettucePoolingClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(10))
                        .clientOptions(clientOptions)
                        .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, poolConfig);
        
        // Test connection
        try {
            factory.afterPropertiesSet();
            factory.getConnection().ping();
            log.info("✅ Redis connection successful to {}:{}", redisHost, redisPort);
        } catch (Exception e) {
            log.error("❌ Redis connection failed to {}:{} - {}", redisHost, redisPort, e.getMessage());
            log.warn("Application will continue with in-memory fallback for caching");
        }
        
        return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template =
                new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        // Use String serializers for both key and value
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(
                new StringRedisSerializer());

        template.afterPropertiesSet();
        log.info("Redis template configured successfully");
        return template;
    }
}