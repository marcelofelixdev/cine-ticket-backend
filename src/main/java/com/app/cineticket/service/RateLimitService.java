package com.app.cineticket.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final Cache<String, Bucket> cacheBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public Bucket getUserBucket(String email) {
        return cacheBuckets.get(email, this::createBucket);
    }

    private Bucket createBucket(String email) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(5).refillGreedy(5,
                        Duration.ofMinutes(1)))
                .build();
    }
}
