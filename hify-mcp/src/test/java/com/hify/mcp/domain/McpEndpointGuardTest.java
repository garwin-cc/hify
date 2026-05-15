package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

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

    @Test
    void rejectsBlockedPortEvenWhenHostIsAllowlisted() {
        McpEndpointGuard guard = new McpEndpointGuard(true, true, "db.internal",
                "22,443,3306", "22,3306,5432", "", false, host -> List.of());

        assertThatThrownBy(() -> guard.validate("https://db.internal:3306/mcp"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("端口");
    }

    @Test
    void rejectsHostWhenDnsResolvesToPrivateAddress() {
        McpEndpointGuard guard = new McpEndpointGuard(true, false, "",
                "443", "22,3306,5432", "", true, host -> List.of(address(10, 1, 2, 3)));

        assertThatThrownBy(() -> guard.validate("https://mcp.example.com/mcp"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网");
    }

    @Test
    void allowsPrivateAddressWhenItMatchesConfiguredCidr() {
        McpEndpointGuard guard = new McpEndpointGuard(true, false, "",
                "443,8080", "22,3306,5432", "10.0.0.0/8", true,
                host -> List.of(address(10, 9, 8, 7)));

        guard.validate("https://mcp.example.com:443/mcp");
        guard.validate("http://10.2.3.4:8080/mcp");
    }

    private static InetAddress address(int first, int second, int third, int fourth) {
        try {
            return InetAddress.getByAddress(new byte[] {
                    (byte) first,
                    (byte) second,
                    (byte) third,
                    (byte) fourth
            });
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
