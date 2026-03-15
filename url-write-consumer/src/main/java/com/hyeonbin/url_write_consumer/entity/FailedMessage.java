package com.hyeonbin.url_write_consumer.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("failed_messages")
public class FailedMessage {

    @PrimaryKey
    @Column("id")
    private UUID id;

    @Column("topic")
    private String topic;

    @Column("partition_id")
    private Integer partitionId;

    @Column("offset_value")
    private Long offsetValue;

    @Column("message_key")
    private String messageKey;

    @Column("payload")
    private String payload;

    @Column("error_message")
    private String errorMessage;

    @Column("failed_at")
    private Instant failedAt;

    public FailedMessage() {}

    public FailedMessage(
        UUID id,
        String topic,
        Integer partitionId,
        Long offsetValue,
        String messageKey,
        String payload,
        String errorMessage,
        Instant failedAt
    ) {
        this.id = id;
        this.topic = topic;
        this.partitionId = partitionId;
        this.offsetValue = offsetValue;
        this.messageKey = messageKey;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.failedAt = failedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getPartitionId() {
        return partitionId;
    }

    public Long getOffsetValue() {
        return offsetValue;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setPartitionId(Integer partitionId) {
        this.partitionId = partitionId;
    }

    public void setOffsetValue(Long offsetValue) {
        this.offsetValue = offsetValue;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }
}
