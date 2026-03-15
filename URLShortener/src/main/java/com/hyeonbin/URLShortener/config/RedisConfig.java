package com.hyeonbin.URLShortener.config;

import io.lettuce.core.ReadFrom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Arrays;

@Configuration
public class RedisConfig {

    @Value("${app.redis.sentinel.master}")
    private String sentinelMaster;

    @Value("${app.redis.sentinel.nodes}")
    private String sentinelNodes;

    @Bean
    public RedisSentinelConfiguration redisSentinelConfiguration() {
        RedisSentinelConfiguration configuration = new RedisSentinelConfiguration();
        configuration.master(sentinelMaster);

        Arrays.stream(sentinelNodes.split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .map(this::toRedisNode)
                .forEach(configuration::addSentinel);

        return configuration;
    }

    @Bean
    @Primary
    public LettuceConnectionFactory primaryConnectionFactory(
            RedisSentinelConfiguration redisSentinelConfiguration) {
        return new LettuceConnectionFactory(redisSentinelConfiguration);
    }

    @Bean
    public LettuceConnectionFactory replicaConnectionFactory(
            RedisSentinelConfiguration redisSentinelConfiguration) {
        LettuceClientConfiguration replicaClientConfiguration = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .build();

        return new LettuceConnectionFactory(redisSentinelConfiguration, replicaClientConfiguration);
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

    private RedisNode toRedisNode(String node) {
        String[] hostAndPort = node.split(":", 2);
        if (hostAndPort.length != 2) {
            throw new IllegalArgumentException("Invalid Redis sentinel node: " + node);
        }

        return new RedisNode(hostAndPort[0].trim(), Integer.parseInt(hostAndPort[1].trim()));
    }
}