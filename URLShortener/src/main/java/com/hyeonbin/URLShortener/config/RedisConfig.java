package com.hyeonbin.URLShortener.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RedisConfig {

    @Value("${app.redis.primary.host}")
    private String primaryHost;

    @Value("${app.redis.primary.port}")
    private int primaryPort;

    @Value("${app.redis.replica.host}")
    private String replicaHost;

    @Value("${app.redis.replica.port}")
    private int replicaPort;

    @Bean
    @Primary
    public LettuceConnectionFactory primaryConnectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(primaryHost, primaryPort));
    }

    @Bean
    public LettuceConnectionFactory replicaConnectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(replicaHost, replicaPort));
    }

    @Bean("primaryRedisTemplate")
    public StringRedisTemplate primaryRedisTemplate(
            @Qualifier("primaryConnectionFactory") LettuceConnectionFactory primaryConnectionFactory) {
        return new StringRedisTemplate(primaryConnectionFactory);
    }

    @Bean("replicaRedisTemplate")
    public StringRedisTemplate replicaRedisTemplate(
            @Qualifier("replicaConnectionFactory") LettuceConnectionFactory replicaConnectionFactory) {
        return new StringRedisTemplate(replicaConnectionFactory);
    }
}