package com.hyeonbin.URLShortener.service;

import com.hyeonbin.URLShortener.entity.Url;
import com.hyeonbin.URLShortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final String CACHE_KEY_PREFIX = "url:";

    private final UrlRepository repository;
    private final StringRedisTemplate primaryRedisTemplate;
    private final StringRedisTemplate replicaRedisTemplate;

    public UrlShortenerService(
            UrlRepository repository,
            @Qualifier("primaryRedisTemplate") StringRedisTemplate primaryRedisTemplate,
            @Qualifier("replicaRedisTemplate") StringRedisTemplate replicaRedisTemplate) {
        this.repository = repository;
        this.primaryRedisTemplate = primaryRedisTemplate;
        this.replicaRedisTemplate = replicaRedisTemplate;
    }

    // Retrieve long URL
    public String find(String shortUrl) {
        String cacheKey = CACHE_KEY_PREFIX + shortUrl;

        // Read path: replica cache first.
        try {
            String cachedLongUrl = replicaRedisTemplate.opsForValue().get(cacheKey);
            if (cachedLongUrl != null) {
                LOGGER.info("Cache HIT for key: {}", cacheKey);
                return cachedLongUrl;
            }
            LOGGER.info("Cache MISS for key: {}", cacheKey); 
        } catch (Exception ex) {
            LOGGER.warn("Failed to read from Redis replica for key {}", cacheKey, ex);
        }

        // Cache miss on replica, read from Cassandra
        Optional<Url> result = repository.findById(shortUrl);
        if (result.isEmpty()) {
            return null;
        }
        String longUrl = result.get().getLongUrl();

        // Cache-aside write-back to primary Redis after DB hit.
        try {
            primaryRedisTemplate.opsForValue().set(cacheKey, longUrl, Duration.ofMinutes(1));
            LOGGER.info("Cache populated for key: {}", cacheKey);
        } catch (Exception ex) {
            LOGGER.warn("Failed to update Redis primary cache for key {}", cacheKey, ex);
        }

        return longUrl;
    }

    // Save short → long mapping
    public void save(String shortUrl, String longUrl) {
        // Write-around: only write to Cassandra, skip Redis.
        Url url = new Url(shortUrl, longUrl, Instant.now());
        repository.save(url);
    }
}
