package com.youthletter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    /**
     * AI Service(Node.js) 호출용 WebClient.
     * Spring Boot가 API Gateway 역할이므로, 프론트엔드는 이 서버만 호출하고
     * AI Service는 내부 네트워크에서만 접근.
     */
    @Bean(name = "aiServiceWebClient")
    public WebClient aiServiceWebClient() {
        return WebClient.builder()
                .baseUrl(aiServiceBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))  // 10MB (뉴스 원문이 클 수 있음)
                .build();
    }

    /**
     * 외부 API (정부 정책 API, 주식 시세 등) 호출용 WebClient.
     */
    @Bean(name = "externalApiWebClient")
    public WebClient externalApiWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024))
                .build();
    }
}
