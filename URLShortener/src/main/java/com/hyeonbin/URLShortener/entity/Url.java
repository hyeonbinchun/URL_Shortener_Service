package com.hyeonbin.URLShortener.entity;

import java.time.Instant;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("urls")
public class Url {
    @PrimaryKey
    @Column("short_url")    
    private String shortUrl;      // matches short_url column

    @Column("long_url")
    private String longUrl;
    
    @Column("created_at")
    private Instant createdAt;

    // Constructor
    public Url() {}
    public Url(String shortUrl, String longUrl, Instant createdAt) {
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }

    // Getters
    public String getShortUrl() { return shortUrl; }
    public String getLongUrl()  { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
