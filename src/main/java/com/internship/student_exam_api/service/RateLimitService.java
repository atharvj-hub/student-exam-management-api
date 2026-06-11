package com.internship.student_exam_api.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RateLimitService {

    /**
     * A bucket paired with the wall-clock time it was last touched. The timestamp
     * lets the scheduled sweep evict IPs that have gone idle, bounding memory.
     */
    private static final class BucketEntry {
        final Bucket bucket;
        final AtomicLong lastAccessMs;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccessMs = new AtomicLong(System.currentTimeMillis());
        }
    }

    // ── Login bucket ──────────────────────────────────────────────────────────
    // Protects against brute-force credential stuffing. Default: 5 attempts/min/IP.
    private final ConcurrentHashMap<String, BucketEntry> loginCache = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.capacity:5}")
    private int loginCapacity;

    // ── Analytics bucket ──────────────────────────────────────────────────────
    // Protects /api/analytics/** from flooding; the /insights endpoint triggers an
    // LLM call, so summary + insights share this bucket and a single client cannot
    // exhaust AI quota by alternating between them. Default: 20 requests/min/IP.
    private final ConcurrentHashMap<String, BucketEntry> analyticsCache = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.analytics-capacity:20}")
    private int analyticsCapacity;

    // ── Eviction tuning ───────────────────────────────────────────────────────
    // Buckets whose IP has been idle longer than this are removed by the sweep.
    @Value("${app.rate-limit.idle-eviction-minutes:10}")
    private long idleEvictionMinutes;

    public Bucket resolveBucket(String ip) {
        return resolve(loginCache, ip, loginCapacity);
    }

    public Bucket resolveAnalyticsBucket(String ip) {
        return resolve(analyticsCache, ip, analyticsCapacity);
    }

    private Bucket resolve(ConcurrentHashMap<String, BucketEntry> cache, String ip, int capacity) {
        BucketEntry entry = cache.computeIfAbsent(ip, k -> new BucketEntry(newBucket(capacity)));
        entry.lastAccessMs.set(System.currentTimeMillis());
        return entry.bucket;
    }

    private Bucket newBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Periodically evicts buckets whose IP has been idle past the TTL, bounding
     * the otherwise-unbounded per-IP map for a public-facing endpoint. An evicted
     * IP simply receives a fresh, full bucket on its next request — which is safe
     * because eviction only happens long after the 1-minute refill window has
     * already elapsed, so no client gains extra allowance by being evicted.
     */
    @Scheduled(fixedDelayString = "${app.rate-limit.eviction-sweep-ms:300000}")
    public void evictIdleBuckets() {
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(idleEvictionMinutes).toMillis();
        int removed = evictOlderThan(loginCache, cutoff) + evictOlderThan(analyticsCache, cutoff);
        if (removed > 0) {
            log.debug("Rate-limit sweep evicted {} idle bucket(s); {} login / {} analytics remain",
                    removed, loginCache.size(), analyticsCache.size());
        }
    }

    private int evictOlderThan(ConcurrentHashMap<String, BucketEntry> cache, long cutoff) {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().lastAccessMs.get() < cutoff);
        return before - cache.size();
    }
}
