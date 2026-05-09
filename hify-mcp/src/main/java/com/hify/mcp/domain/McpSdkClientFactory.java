package com.hify.mcp.domain;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class McpSdkClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String SSE_ENDPOINT = "/sse";

    public McpSyncClient create(String endpoint) {
        McpClientTransport transport = createTransport(normalizeEndpoint(endpoint));
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(CONNECT_TIMEOUT)
                .build();
        try {
            client.initialize();
            return client;
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }

    private McpClientTransport createTransport(String endpoint) {
        if (endpoint.endsWith(SSE_ENDPOINT)) {
            String baseUri = endpoint.substring(0, endpoint.length() - SSE_ENDPOINT.length());
            return HttpClientSseClientTransport.builder(baseUri)
                    .sseEndpoint(SSE_ENDPOINT)
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }
        return HttpClientStreamableHttpTransport.builder(endpoint)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint == null ? "" : endpoint.replaceAll("\\s+", "");
    }
}
