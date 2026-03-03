package com.hyeonbin.URLShortener.controller;

import com.hyeonbin.URLShortener.service.UrlShortenerService;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.core.io.Resource;

@RestController
public class UrlShortenerController {
    private final UrlShortenerService service;
    
    public UrlShortenerController(UrlShortenerService service) {
        this.service = service;
    }
 
     // PUT /?short=abc&long=https://example.com
    @PutMapping("/")
    public ResponseEntity<Resource> recordRedirect(
            @RequestParam("short") String shortURL,
            @RequestParam("long") String longURL) {
                // print shortURL and longURL
                System.out.println("Received shortURL: " + shortURL);
                System.out.println("Received longURL: " + longURL);
        try {
            service.save(shortURL, longURL);
            return serveHtml("redirect_recorded.html", HttpStatus.OK);
        } catch (IOException e) {
            return serveHtml("404.html", HttpStatus.NOT_FOUND);
        }
    }

    // GET /abc → 301 + redirect.html body
    @GetMapping("/{shortURL}")
    public ResponseEntity<Resource> redirect(@PathVariable String shortURL) {
        try {
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
        } catch (IOException e) {
            return serveHtml("404.html", HttpStatus.NOT_FOUND);
        }
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
