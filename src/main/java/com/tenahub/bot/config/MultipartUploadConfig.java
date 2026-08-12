package com.tenahub.bot.config;

import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class MultipartUploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement(
            @Value("${spring.servlet.multipart.max-file-size:25MB}") String maxFileSize,
            @Value("${spring.servlet.multipart.max-request-size:50MB}") String maxRequestSize) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.parse(maxFileSize));
        factory.setMaxRequestSize(DataSize.parse(maxRequestSize));
        return factory.createMultipartConfig();
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatUploadCustomizer(
            @Value("${spring.servlet.multipart.max-request-size:50MB}") String maxRequestSize) {
        return factory -> factory.addConnectorCustomizers(this::customizeConnector);
    }

    private void customizeConnector(Connector connector) {
        connector.setMaxPostSize(50 * 1024 * 1024);
        connector.setMaxPartCount(50);
        connector.setMaxPartHeaderSize(1024 * 1024);
    }
}