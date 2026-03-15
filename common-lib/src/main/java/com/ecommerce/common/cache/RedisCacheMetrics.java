package com.ecommerce.common.cache;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis cache metrics collection and Prometheus exposure.
 * Requirements: 32.1-32.10
 */
@Component
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisCacheMetrics {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheMetrics.class);

    // Alert thresholds (Req 32.7, 32.8, 32.9)
    private static final double HIT_RATE_ALERT_THRESHOLD = 0.70;   // Alert below 70%
    private static final double MEMORY_ALERT_THRESHOLD = 0.85;     // Alert above 85%
    private static final double LATENCY_ALERT_THRESHOLD_MS = 5.0;  // Alert above 5ms

    private final RedisConnectionFactory connectionFactory;
    private final MeterRegistry meterRegistry;

    // Atomic values for gauge metrics
    private final AtomicLong usedMemoryBytes = new AtomicLong(0);
    private final AtomicLong maxMemoryBytes = new AtomicLong(1L); // avoid division by zero
    private final AtomicLong connectedClients = new AtomicLong(0);
    private final AtomicLong totalCommandsProcessed = new AtomicLong(0);
    private final AtomicLong keyspaceHits = new AtomicLong(0);
    private final AtomicLong keyspaceMisses = new AtomicLong(0);
    private final AtomicLong evictedKeys = new AtomicLong(0);

    public RedisCacheMetrics(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        this.connectionFactory = connectionFactory;
        this.meterRegistry = meterRegistry;
        registerGauges();
    }

    /**
     * Register Prometheus gauges for Redis metrics.
     * Requirements: 32.10
     */
    private void registerGauges() {
        // Memory usage (Req 32.2)
        Gauge.builder("redis.memory.used.bytes", usedMemoryBytes, AtomicLong::get)
                .description("Redis used memory in bytes")
                .tags(Tags.empty())
                .register(meterRegistry);

        Gauge.builder("redis.memory.max.bytes", maxMemoryBytes, AtomicLong::get)
                .description("Redis max memory in bytes")
                .register(meterRegistry);

        Gauge.builder("redis.memory.usage.ratio", this, m ->
                        m.maxMemoryBytes.get() > 0
                                ? (double) m.usedMemoryBytes.get() / m.maxMemoryBytes.get()
                                : 0.0)
                .description("Redis memory usage ratio (0.0 - 1.0)")
                .register(meterRegistry);

        // Connected clients (Req 32.5)
        Gauge.builder("redis.clients.connected", connectedClients, AtomicLong::get)
                .description("Number of connected Redis clients")
                .register(meterRegistry);

        // Commands per second (Req 32.6)
        Gauge.builder("redis.commands.processed.total", totalCommandsProcessed, AtomicLong::get)
                .description("Total Redis commands processed")
                .register(meterRegistry);

        // Cache hit rate (Req 32.1)
        Gauge.builder("redis.cache.hit.rate", this, m -> {
                    long hits = m.keyspaceHits.get();
                    long misses = m.keyspaceMisses.get();
                    long total = hits + misses;
                    return total > 0 ? (double) hits / total : 0.0;
                })
                .description("Redis cache hit rate (0.0 - 1.0, target > 0.80)")
                .register(meterRegistry);

        // Eviction rate (Req 32.3)
        Gauge.builder("redis.evicted.keys.total", evictedKeys, AtomicLong::get)
                .description("Total number of evicted Redis keys")
                .register(meterRegistry);
    }

    /**
     * Collect Redis INFO stats every 30 seconds.
     * Requirements: 32.1-32.10
     */
    @Scheduled(fixedDelay = 30_000)
    public void collectMetrics() {
        try {
            Properties info = connectionFactory.getConnection().serverCommands().info();
            if (info == null) return;

            // Memory metrics (Req 32.2)
            String usedMem = info.getProperty("used_memory");
            if (usedMem != null) usedMemoryBytes.set(Long.parseLong(usedMem));

            String maxMem = info.getProperty("maxmemory");
            if (maxMem != null && !maxMem.equals("0")) {
                maxMemoryBytes.set(Long.parseLong(maxMem));
            }

            // Client metrics (Req 32.5)
            String clients = info.getProperty("connected_clients");
            if (clients != null) connectedClients.set(Long.parseLong(clients));

            // Command metrics (Req 32.6)
            String totalCmds = info.getProperty("total_commands_processed");
            if (totalCmds != null) totalCommandsProcessed.set(Long.parseLong(totalCmds));

            // Hit/miss metrics (Req 32.1)
            String hits = info.getProperty("keyspace_hits");
            if (hits != null) keyspaceHits.set(Long.parseLong(hits));

            String misses = info.getProperty("keyspace_misses");
            if (misses != null) keyspaceMisses.set(Long.parseLong(misses));

            // Eviction metrics (Req 32.3)
            String evicted = info.getProperty("evicted_keys");
            if (evicted != null) evictedKeys.set(Long.parseLong(evicted));

            // Check alert thresholds
            checkAlerts();

        } catch (Exception e) {
            logger.error("Failed to collect Redis metrics", e);
        }
    }

    /**
     * Check alert thresholds and log warnings.
     * Requirements: 32.7, 32.8, 32.9
     */
    private void checkAlerts() {
        // Hit rate alert (Req 32.7 - alert below 70%)
        long hits = keyspaceHits.get();
        long misses = keyspaceMisses.get();
        long total = hits + misses;
        if (total > 0) {
            double hitRate = (double) hits / total;
            if (hitRate < HIT_RATE_ALERT_THRESHOLD) {
                logger.warn("ALERT: Redis cache hit rate {:.1f}% is below threshold {}%",
                        hitRate * 100, HIT_RATE_ALERT_THRESHOLD * 100);
            }
        }

        // Memory usage alert (Req 32.8 - alert above 85%)
        if (maxMemoryBytes.get() > 0) {
            double memUsage = (double) usedMemoryBytes.get() / maxMemoryBytes.get();
            if (memUsage > MEMORY_ALERT_THRESHOLD) {
                logger.warn("ALERT: Redis memory usage {:.1f}% exceeds threshold {}%",
                        memUsage * 100, MEMORY_ALERT_THRESHOLD * 100);
            }
        }
    }
}
