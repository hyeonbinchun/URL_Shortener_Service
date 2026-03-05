package com.hyeonbin.URLShortener.service;

import com.hyeonbin.URLShortener.entity.Url;
import com.hyeonbin.URLShortener.repository.UrlRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private final UrlRepository repository;

    public UrlShortenerService(UrlRepository repository) {
        this.repository = repository;
    }

    // Retrieve long URL
    public String find(String shortUrl) {
        Optional<Url> result = repository.findById(shortUrl);
        return result.map(Url::getLongUrl).orElse(null);
    }

   // Save short → long mapping
    public void save(String shortUrl, String longUrl) {
        Url url = new Url(shortUrl, longUrl, Instant.now());
        repository.save(url);
    }
}
