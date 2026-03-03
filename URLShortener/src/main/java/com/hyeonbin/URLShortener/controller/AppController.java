package com.hyeonbin.URLShortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {
    @GetMapping("/")
    public String home() {
        return "Welcome to the URL Shortener Service!";     
    }
}
