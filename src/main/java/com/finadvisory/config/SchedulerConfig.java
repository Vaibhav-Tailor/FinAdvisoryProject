package com.finadvisory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * SchedulerConfig
 * ─────────────────────────────────────────────────────────────────
 * @EnableScheduling activates Spring's @Scheduled annotation processing.
 * Without this, NavSyncScheduler's @Scheduled method is ignored.
 *
 * Also defines the RestTemplate bean used by NavSyncScheduler
 * to fetch AMFI NAVAll.txt with sensible timeout settings.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * SimpleClientHttpRequestFactory with timeout configuration.
     *
     * Connect timeout: 30 seconds
     *   — Time allowed to establish HTTP connection to AMFI server.
     *
     * Read timeout: 120 seconds
     *   — Time allowed to read the full response body.
     *   — AMFI NAVAll.txt is ~3–5 MB of text. 120s is generous but safe.
     *
     * AMFI's server can be slow — generous timeouts prevent false failures.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000); // 30 seconds in ms
        factory.setReadTimeout(120_000); // 120 seconds in ms
        
return new RestTemplate(factory);
    }
}

