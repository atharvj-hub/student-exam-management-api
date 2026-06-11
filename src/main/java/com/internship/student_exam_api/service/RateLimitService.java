package com.internship.student_exam_api.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    // ── Login bucket ──────────────────────────────────────────────────────────
    // Protects against brute-force credential stuffing.
    // Default: 5 attempts per minute per IP.
    private final ConcurrentHashMap<String, Bucket> loginCache = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.capacity:5}")
    private int loginCapacity;

    public Bucket resolveBucket(String ip) {
        return loginCache.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(loginCapacity, Refill.greedy(loginCapacity, Duration.ofMinutes(1))))
                        .build());
    }

    // ── Analytics bucket ──────────────────────────────────────────────────────
    // Protects /api/analytics/** from flooding; the /insights endpoint triggers
    // a paid LLM call so keeping this well below login capacity is intentional.
    // Default: 20 requests per minute per IP.
    //   - Summary endpoint: cheap SQL; 20/min is generous for legitimate use.
    //   - Insights endpoint: LLM call; the same 20/min limit still prevents abuse.
    //   Both endpoints share this bucket so a single client cannot exhaust AI
    //   quota by hitting summary + insights alternately.
    private final ConcurrentHashMap<String, Bucket> analyticsCache = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.analytics-capacity:20}")
    private int analyticsCapacity;

    public Bucket resolveAnalyticsBucket(String ip) {
        return analyticsCache.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(analyticsCapacity, Refill.greedy(analyticsCapacity, Duration.ofMinutes(1))))
                        .build());
    }
}
