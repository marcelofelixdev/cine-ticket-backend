package com.app.cineticket.service;

import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> cacheBuckets = new ConcurrentHashMap<>();

    public Bucket getUserBucket(String email) {
        return cacheBuckets.computeIfAbsent(email, this::createBucket);
    }

    private Bucket createBucket(String email) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(5).refillGreedy(5,
                        Duration.ofMinutes(1)))
                .build();
    }
}
