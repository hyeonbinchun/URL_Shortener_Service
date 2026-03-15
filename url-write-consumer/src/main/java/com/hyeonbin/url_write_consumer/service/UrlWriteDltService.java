package com.hyeonbin.url_write_consumer.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;

import com.hyeonbin.url_write_consumer.entity.FailedMessage;
import com.hyeonbin.url_write_consumer.repository.FailedMessageRepository;

@Service
public class UrlWriteDltService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UrlWriteDltService.class);
    private static final String UNKNOWN_ERROR = "unknown";

    private final FailedMessageRepository failedMessageRepository;

    public UrlWriteDltService(FailedMessageRepository failedMessageRepository) {
        this.failedMessageRepository = failedMessageRepository;
    }

    @KafkaListener(
        topics = "${spring.kafka.topic.url-write}.dlt",
        groupId = "url-write-dlt-group"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String errorMessage = extractErrorMessage(record);

        FailedMessage failedMessage = new FailedMessage(
            UUID.randomUUID(),
            record.topic(),
            record.partition(),
            record.offset(),
            record.key(),
            record.value(),
            errorMessage,
            Instant.now()
        );

        try {
            failedMessageRepository.save(failedMessage);
            LOGGER.error(
                "Saved DLT message id={} topic={} partition={} offset={} key={} error={} payload={}",
                failedMessage.getId(),
                failedMessage.getTopic(),
                failedMessage.getPartitionId(),
                failedMessage.getOffsetValue(),
                failedMessage.getMessageKey(),
                failedMessage.getErrorMessage(),
                failedMessage.getPayload()
            );
        } catch (Exception ex) {
            LOGGER.error(
                "Failed to persist DLT message topic={} partition={} offset={} payload={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.value(),
                ex
            );
        }
    }

    private String extractErrorMessage(ConsumerRecord<String, String> record) {
        Header exceptionHeader = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        if (exceptionHeader == null || exceptionHeader.value() == null) {
            return UNKNOWN_ERROR;
        }
        return new String(exceptionHeader.value(), StandardCharsets.UTF_8);
    }
}
