package com.hyeonbin.URLShortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyeonbin.URLShortener.entity.Url;
import com.hyeonbin.URLShortener.kafka.UrlWriteMessage;
import com.hyeonbin.URLShortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final UrlRepository repository;
    private final ObjectProvider<StringRedisTemplate> primaryRedisTemplateProvider;
    private final ObjectProvider<StringRedisTemplate> replicaRedisTemplateProvider;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topic.url-write}")
    private String urlWriteTopic;

    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.write.mode:kafka}")
    private String writeMode;

    public UrlShortenerService(
            UrlRepository repository,
            @Qualifier("primaryRedisTemplate") ObjectProvider<StringRedisTemplate> primaryRedisTemplateProvider,
            @Qualifier("replicaRedisTemplate") ObjectProvider<StringRedisTemplate> replicaRedisTemplateProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.primaryRedisTemplateProvider = primaryRedisTemplateProvider;
        this.replicaRedisTemplateProvider = replicaRedisTemplateProvider;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // READ PATH: replica cache → Cassandra fallback → populate primary cache
    // -------------------------------------------------------------------------
    public String find(String shortUrl) {
        String cacheKey = CACHE_KEY_PREFIX + shortUrl;

        if (cacheEnabled) {
            // 1. Try redis replica first
            try {
                StringRedisTemplate replicaRedisTemplate = replicaRedisTemplateProvider.getIfAvailable();
                if (replicaRedisTemplate != null) {
                    String cachedLongUrl = replicaRedisTemplate.opsForValue().get(cacheKey);
                    if (cachedLongUrl != null) {
                        LOGGER.debug("Cache HIT for key: {}", cacheKey);
                        return cachedLongUrl;
                    }
                    LOGGER.debug("Cache MISS for key: {}", cacheKey);
                } else {
                    LOGGER.warn("Redis replica template is unavailable for key {}", cacheKey);
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to read from Redis replica for key {}", cacheKey, ex);
            }
        } else {
            // LOGGER.info("Cache disabled; skipping Redis read for key: {}", cacheKey);
        }

        // 2. Cache miss - fall back to Cassandra
        Optional<Url> result = repository.findById(shortUrl);
        if (result.isEmpty()) {
            return null;
        }
        String longUrl = result.get().getLongUrl();

        // 3. Populate primary cache (replicates to replicas)
        if (cacheEnabled) {
            try {
                StringRedisTemplate primaryRedisTemplate = primaryRedisTemplateProvider.getIfAvailable();
                if (primaryRedisTemplate != null) {
                    primaryRedisTemplate.opsForValue().set(cacheKey, longUrl, CACHE_TTL);
                    LOGGER.debug("Cache populated for key: {}", cacheKey);
                } else {
                    LOGGER.warn("Redis primary template is unavailable for key {}", cacheKey);
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to update Redis primary cache for key {}", cacheKey, ex);
            }
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
            // LOGGER.info("Direct write mode enabled. Saved URL mapping to Cassandra for key: {}", shortUrl);
            return;
        }

        UrlWriteMessage message = new UrlWriteMessage(
            shortUrl,
            longUrl,
            Instant.now().toString() // ISO-8601, safe for JSON
        );
        String json = toJsonString(message);
        kafkaTemplate.send(urlWriteTopic, shortUrl, json);
        // LOGGER.info("Published URL write event to Kafka topic '{}' for key: {}", urlWriteTopic, shortUrl);
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
