package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpEndpointGuardTest {

    @Test
    void allowsHttpsEndpoint() {
        McpEndpointGuard guard = new McpEndpointGuard();

        guard.validate("https://api.example.com/mcp");
    }

    @Test
    void rejectsUnsupportedProtocol() {
        McpEndpointGuard guard = new McpEndpointGuard();

        assertThatThrownBy(() -> guard.validate("file:///etc/passwd"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("协议");
    }

    @Test
    void rejectsLocalhostAndMetadataAddress() {
        McpEndpointGuard guard = new McpEndpointGuard();

        assertThatThrownBy(() -> guard.validate("http://127.0.0.1:8080/mcp"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> guard.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
    }

    @Test
    void allowsConfiguredPrivateHost() {
        McpEndpointGuard guard = new McpEndpointGuard(true, true, "10.0.0.8", "443,8080");

        guard.validate("http://10.0.0.8:8080/mcp");
    }
}
