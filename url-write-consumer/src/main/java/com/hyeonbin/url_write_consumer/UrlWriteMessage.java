package com.hyeonbin.url_write_consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    public static UrlWriteMessage fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, UrlWriteMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}