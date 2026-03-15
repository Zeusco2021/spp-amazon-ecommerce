package com.ecommerce.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch client configuration.
 * Connects to Elasticsearch using URI from environment variable ELASTICSEARCH_URI.
 * Requirements: 5.9, 17.8
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.ecommerce.search.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        // Strip http:// or https:// prefix if present, as ClientConfiguration expects host:port
        String hostAndPort = elasticsearchUri
                .replace("https://", "")
                .replace("http://", "");

        return ClientConfiguration.builder()
                .connectedTo(hostAndPort)
                .build();
    }
}
