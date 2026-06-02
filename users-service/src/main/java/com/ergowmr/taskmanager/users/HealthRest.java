package com.ergowmr.taskmanager.users;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthRest {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "service", "users-service",
            "version", "0.0.1",
            "by", "Younes OUAMAR"
        );
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "status", "running",
            "hostname", System.getenv().getOrDefault("HOSTNAME", "unknown")
        );
    }
}
