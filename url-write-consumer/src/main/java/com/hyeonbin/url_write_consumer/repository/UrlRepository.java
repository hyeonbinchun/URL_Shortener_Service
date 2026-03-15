package com.hyeonbin.url_write_consumer.repository;

import com.hyeonbin.url_write_consumer.entity.Url;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface UrlRepository extends CassandraRepository<Url, String> {
    // findById(shortUrl) is inherited
}
