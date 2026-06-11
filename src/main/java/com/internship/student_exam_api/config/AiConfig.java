package com.internship.student_exam_api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Applies connect/read timeouts to every auto-configured {@code RestClient.Builder},
     * including the one Spring AI uses to call Ollama. Without a read timeout a hung
     * model response holds the servlet thread until the container's (effectively
     * unbounded) default — under concurrency that exhausts the thread pool and takes
     * the whole API down. On expiry the read times out with a {@code SocketTimeoutException},
     * which RestClient wraps as {@code ResourceAccessException}; {@code StudentInsightService}
     * maps that to a 503 (AiProviderUnavailableException).
     */
    @Bean
    public RestClientCustomizer aiRestClientCustomizer(
            @Value("${app.ai.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms:120000}") long readTimeoutMs) {
        return restClientBuilder -> restClientBuilder.requestFactory(
                ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                                .withReadTimeout(Duration.ofMillis(readTimeoutMs))));
    }
}
