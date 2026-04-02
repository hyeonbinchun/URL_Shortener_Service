package com.hyeonbin.URLShortener.controller;

import com.hyeonbin.URLShortener.service.UrlShortenerService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;
import org.springframework.core.io.Resource;

@RestController
public class UrlShortenerController {

    private final UrlShortenerService service;
    private final ObjectProvider<StringRedisTemplate> primaryRedisTemplateProvider;
    private final ObjectProvider<StringRedisTemplate> replicaRedisTemplateProvider;

    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;
    
    public UrlShortenerController(
            UrlShortenerService service,
            @Qualifier("primaryRedisTemplate") ObjectProvider<StringRedisTemplate> primaryRedisTemplateProvider,
            @Qualifier("replicaRedisTemplate") ObjectProvider<StringRedisTemplate> replicaRedisTemplateProvider) {
        this.service = service;
        this.primaryRedisTemplateProvider = primaryRedisTemplateProvider;
        this.replicaRedisTemplateProvider = replicaRedisTemplateProvider;
    }
 
     // PUT /?short=abc&long=https://example.com
    @PutMapping("/")
    public ResponseEntity<Resource> recordRedirect(
            @RequestParam("short") String shortURL,
            @RequestParam("long") String longURL) {       
                service.save(shortURL, longURL);
                return serveHtml("redirect_recorded.html", HttpStatus.OK);
    } 

    // GET /abc → 301 + redirect.html body
    @GetMapping("/{shortURL}")
    public ResponseEntity<Resource> redirect(@PathVariable String shortURL) {
        String longURL = service.find(shortURL);
        if (longURL != null) {
            ClassPathResource file = new ClassPathResource("static/redirect.html");
            return ResponseEntity
                    .status(HttpStatus.MOVED_PERMANENTLY)
                    .location(URI.create(longURL))
                    .contentType(MediaType.TEXT_HTML)
                    .body(file);
        } else {
            return serveHtml("404.html", HttpStatus.NOT_FOUND);
        }
    }

    // Temporary debug endpoint to check cache status for a given short URL
    @GetMapping("/debug/cache/{shortUrl}")
    public Map<String, String> debugCache(@PathVariable String shortUrl) {
        if (!cacheEnabled) {
            return Map.of(
                "replica", "DISABLED",
                "primary", "DISABLED"
            );
        }

        String cacheKey = "url:" + shortUrl;

        StringRedisTemplate replicaRedisTemplate = replicaRedisTemplateProvider.getIfAvailable();
        StringRedisTemplate primaryRedisTemplate = primaryRedisTemplateProvider.getIfAvailable();

        String fromReplica = replicaRedisTemplate != null ? replicaRedisTemplate.opsForValue().get(cacheKey) : null;
        String fromPrimary = primaryRedisTemplate != null ? primaryRedisTemplate.opsForValue().get(cacheKey) : null;

        return Map.of(
            "replica", fromReplica != null ? fromReplica : "MISS",
            "primary", fromPrimary != null ? fromPrimary : "MISS"
        );
    }

    // Mirrors your original sendFile()
    private ResponseEntity<Resource> serveHtml(String filename, HttpStatus status) {
        ClassPathResource file = new ClassPathResource("static/" + filename);
        return ResponseEntity
                .status(status)
                .contentType(MediaType.TEXT_HTML)
                .body(file);
    }
}
