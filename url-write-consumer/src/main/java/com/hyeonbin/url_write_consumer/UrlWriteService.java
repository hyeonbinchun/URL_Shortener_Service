package com.hyeonbin.url_write_consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class UrlWriteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlWriteService.class);
    
    @KafkaListener(
        topics = "${spring.kafka.topic.url-write}",   // pulled from application.yaml
        groupId = "${spring.kafka.consumer.group-id}"
    )

    public void consume(String message) {
        LOGGER.info("Received message from Kafka: {}", message);
        UrlWriteMessage urlWriteMessage = UrlWriteMessage.fromJson(message);

        // TODO: Save urlWriteMessage to Cassandra
        
    }

}
