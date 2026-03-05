package com.hyeonbin.URLShortener.repository;

import com.hyeonbin.URLShortener.entity.Url;
import org.springframework.data.cassandra.repository.CassandraRepository;

// Spring Data generates the actual query implementations for you
public interface UrlRepository extends CassandraRepository<Url, String> {
    // findById(shortUrl) is inherited — no code needed
}
