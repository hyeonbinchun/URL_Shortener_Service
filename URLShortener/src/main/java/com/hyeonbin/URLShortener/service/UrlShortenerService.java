package com.hyeonbin.URLShortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyeonbin.URLShortener.entity.Url;
import com.hyeonbin.URLShortener.kafka.UrlWriteMessage;
import com.hyeonbin.URLShortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlShortenerService.class);
    private static final String CACHE_KEY_PREFIX = "url:";

    private final UrlRepository repository;
    private final StringRedisTemplate primaryRedisTemplate;
    private final StringRedisTemplate replicaRedisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic.url-write}")
    private String urlWriteTopic;

    @Value("${app.write.mode:kafka}")
    private String writeMode;

    public UrlShortenerService(
            UrlRepository repository,
            @Qualifier("primaryRedisTemplate") StringRedisTemplate primaryRedisTemplate,
            @Qualifier("replicaRedisTemplate") StringRedisTemplate replicaRedisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.primaryRedisTemplate = primaryRedisTemplate;
        this.replicaRedisTemplate = replicaRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // READ PATH: replica cache → Cassandra fallback → populate primary cache
    // -------------------------------------------------------------------------
    public String find(String shortUrl) {
        String cacheKey = CACHE_KEY_PREFIX + shortUrl;

        // 1. Try redis replica first
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

        // 2. Cache miss - fall back to Cassandra
        Optional<Url> result = repository.findById(shortUrl);
        if (result.isEmpty()) {
            return null;
        }
        String longUrl = result.get().getLongUrl();

        // 3. Populate primary cache (replicates to replicas)
        try {
            primaryRedisTemplate.opsForValue().set(cacheKey, longUrl, Duration.ofMinutes(1));
            LOGGER.info("Cache populated for key: {}", cacheKey);
        } catch (Exception ex) {
            LOGGER.warn("Failed to update Redis primary cache for key {}", cacheKey, ex);
        }

        return longUrl;
    }


    // -------------------------------------------------------------------------
    // WRITE PATH: publish to Kafka → Writer Service handles Cassandra write
    // -------------------------------------------------------------------------
    public void save(String shortUrl, String longUrl) {
        String normalizedWriteMode = writeMode == null ? "kafka" : writeMode.trim().toLowerCase(Locale.ROOT);

        if ("direct".equals(normalizedWriteMode)) {
            repository.save(new Url(shortUrl, longUrl, Instant.now()));
            LOGGER.info("Direct write mode enabled. Saved URL mapping to Cassandra for key: {}", shortUrl);
            return;
        }

        UrlWriteMessage message = new UrlWriteMessage(
            shortUrl,
            longUrl,
            Instant.now().toString() // ISO-8601, safe for JSON
        );
        String json = toJsonString(message);
        kafkaTemplate.send(urlWriteTopic, shortUrl, json);
        LOGGER.info("Published URL write event to Kafka topic '{}' for key: {}", urlWriteTopic, shortUrl);
    }


    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------
    private String toJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message to JSON", e);
        }
    }
}
