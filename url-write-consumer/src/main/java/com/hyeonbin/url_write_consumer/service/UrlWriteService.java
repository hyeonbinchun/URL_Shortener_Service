package com.hyeonbin.url_write_consumer.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.hyeonbin.url_write_consumer.entity.Url;
import com.hyeonbin.url_write_consumer.kafka.UrlWriteMessage;
import com.hyeonbin.url_write_consumer.repository.UrlRepository;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;

@Service
public class UrlWriteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlWriteService.class);

    private final UrlRepository urlRepository;

    public UrlWriteService(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        dltTopicSuffix = ".dlt"
    )

    @KafkaListener(
        topics = "${spring.kafka.topic.url-write}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        // LOGGER.info("Received message from Kafka: {}", message);
        UrlWriteMessage urlWriteMessage = UrlWriteMessage.fromJson(message);
        // test for failed message
        if (urlWriteMessage.getShortUrl().equals("fail-retry")) {
            LOGGER.warn("Simulating failure for shortUrl='fail-retry'");
            throw new RuntimeException("Simulated failure for testing");
        }
        Url url = new Url(
            urlWriteMessage.getShortUrl(),
            urlWriteMessage.getLongUrl(),
            Instant.parse(urlWriteMessage.getCreatedAt())
        );
        urlRepository.save(url);
        // LOGGER.info("Saved to Cassandra: shortUrl={}", url.getShortUrl());
    }
}
