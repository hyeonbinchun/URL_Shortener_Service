package com.hyeonbin.URLShortener.kafka;

/**
 * Kafka message payload for a URL write event.
 * Produced by the API Service, consumed by the Writer Service.
 */
public class UrlWriteMessage {

    private String shortUrl;
    private String longUrl;
    private String createdAt; // ISO-8601 string — safe for JSON serialization

    public UrlWriteMessage() {}

    public UrlWriteMessage(String shortUrl, String longUrl, String createdAt) {
        this.shortUrl = shortUrl;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }

    public String getShortUrl()  { return shortUrl; }
    public String getLongUrl()   { return longUrl; }
    public String getCreatedAt() { return createdAt; }

    public void setShortUrl(String shortUrl)   { this.shortUrl = shortUrl; }
    public void setLongUrl(String longUrl)     { this.longUrl = longUrl; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}