package com.internship.student_exam_api.service;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the rate limiter's bucket resolution and — the part that matters for
 * production — that idle buckets are evicted so the per-IP map cannot grow without
 * bound. Eviction is observed behaviorally: a retained bucket keeps its consumed
 * state, while an evicted IP is handed a fresh, full bucket on its next request.
 */
class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
        ReflectionTestUtils.setField(service, "loginCapacity", 2);
        ReflectionTestUtils.setField(service, "analyticsCapacity", 2);
        ReflectionTestUtils.setField(service, "idleEvictionMinutes", 10L);
    }

    @Test
    void resolveBucket_returnsStableBucketForSameIp() {
        Bucket bucket = service.resolveBucket("1.2.3.4");
        assertTrue(bucket.tryConsume(2));            // exhaust the 2-token bucket
        // Same IP must map to the SAME bucket — now empty.
        assertFalse(service.resolveBucket("1.2.3.4").tryConsume(1));
    }

    @Test
    void separateIpsGetSeparateBuckets() {
        assertTrue(service.resolveBucket("10.0.0.1").tryConsume(2));
        // A different IP is unaffected by the first IP's consumption.
        assertTrue(service.resolveBucket("10.0.0.2").tryConsume(2));
    }

    @Test
    void evictIdleBuckets_withinTtl_retainsBucketState() {
        service.resolveBucket("9.9.9.9").tryConsume(2);   // exhaust
        service.evictIdleBuckets();                       // TTL is 10 min → not idle
        // Bucket retained → still empty.
        assertFalse(service.resolveBucket("9.9.9.9").tryConsume(1));
    }

    @Test
    void evictIdleBuckets_pastTtl_removesBucket() {
        service.resolveBucket("8.8.8.8").tryConsume(2);   // exhaust
        // Negative TTL puts the cutoff in the future → every entry is considered idle.
        ReflectionTestUtils.setField(service, "idleEvictionMinutes", -1L);
        service.evictIdleBuckets();
        // Evicted → next request gets a fresh, full bucket.
        assertTrue(service.resolveBucket("8.8.8.8").tryConsume(2));
    }

    @Test
    void evictIdleBuckets_sweepsBothLoginAndAnalyticsMaps() {
        service.resolveBucket("7.7.7.7").tryConsume(2);
        service.resolveAnalyticsBucket("7.7.7.7").tryConsume(2);
        ReflectionTestUtils.setField(service, "idleEvictionMinutes", -1L);
        service.evictIdleBuckets();
        assertTrue(service.resolveBucket("7.7.7.7").tryConsume(2));
        assertTrue(service.resolveAnalyticsBucket("7.7.7.7").tryConsume(2));
    }
}
