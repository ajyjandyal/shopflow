package com.shopflow.config;

import com.shopflow.security.InternalApiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Timeouts are essential in distributed systems. Without them, one slow product-service
 * would hold order-service threads forever until order-service itself stops responding
 * (a "cascading failure").
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class ProductClientConfig {

    @Bean
    public RestClient productRestClient(RestClient.Builder builder,
                                        @Value("${app.services.product-url}") String productServiceUrl,
                                        @Value("${app.services.connect-timeout:PT2S}") Duration connectTimeout,
                                        @Value("${app.services.read-timeout:PT5S}") Duration readTimeout,
                                        InternalApiProperties internalApi) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(productServiceUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalApiProperties.HEADER, internalApi.apiKey())
                .build();
    }
}
