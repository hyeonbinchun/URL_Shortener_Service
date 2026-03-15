package com.hyeonbin.url_write_consumer.repository;

import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;

import com.hyeonbin.url_write_consumer.entity.FailedMessage;

public interface FailedMessageRepository extends CassandraRepository<FailedMessage, UUID> {
}
