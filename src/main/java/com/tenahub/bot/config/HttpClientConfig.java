package com.tenahub.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@Configuration
public class HttpClientConfig {

    private static final String NGROK_SKIP_WARNING_HEADER = "ngrok-skip-browser-warning";
    private static final String NGROK_SKIP_WARNING_VALUE = "true";

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestInterceptor ngrokHeaderInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(ngrokHeaderInterceptor));
        return restTemplate;
    }

    @Bean
    public ClientHttpRequestInterceptor ngrokHeaderInterceptor() {
        return (request, body, execution) -> {
            URI uri = request.getURI();
            if (isNgrokHost(uri)) {
                request.getHeaders().set(NGROK_SKIP_WARNING_HEADER, NGROK_SKIP_WARNING_VALUE);
            }
            return execution.execute(request, body);
        };
    }

    private boolean isNgrokHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }

        String host = uri.getHost().toLowerCase();
        return host.contains("ngrok");
    }
}