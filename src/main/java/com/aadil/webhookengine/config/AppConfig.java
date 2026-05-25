package com.aadil.webhookengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * General application beans.
 *
 * RestTemplate is used (per spec constraint) for all outbound HTTP calls
 * to subscriber callback URLs.
 *
 * Timeouts are critical here:
 *   - connectTimeout 5s  → don't hang forever if the subscriber host is down
 *   - readTimeout    10s → don't block a delivery thread if the subscriber
 *                          is slow to respond
 * Without these, a single unresponsive subscriber would tie up a thread
 * indefinitely and starve the pool under load.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 seconds
        factory.setReadTimeout(10_000);     // 10 seconds
        return new RestTemplate(factory);
    }
}
