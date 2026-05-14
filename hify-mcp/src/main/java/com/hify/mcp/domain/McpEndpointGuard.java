package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class McpEndpointGuard {

    private final boolean allowHttp;
    private final boolean allowPrivateNetwork;
    private final Set<String> allowlist;
    private final Set<Integer> allowedPorts;

    public McpEndpointGuard() {
        this(false, false, "", "443");
    }

    public McpEndpointGuard(@Value("${hify.mcp.security.allow-http:false}") boolean allowHttp,
                            @Value("${hify.mcp.security.allow-private-network:false}") boolean allowPrivateNetwork,
                            @Value("${hify.mcp.security.endpoint-allowlist:}") String allowlist,
                            @Value("${hify.mcp.security.allowed-ports:443}") String allowedPorts) {
        this.allowHttp = allowHttp;
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.allowlist = split(allowlist);
        this.allowedPorts = split(allowedPorts).stream()
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    public void validate(String endpoint) {
        URI uri = parse(endpoint);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            if (!"https".equals(scheme) && !("http".equals(scheme) && allowHttp)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 协议不允许: " + scheme);
            }
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint host 不能为空");
        }
        String normalizedHost = host.toLowerCase();
        boolean allowedHost = isAllowlisted(normalizedHost);
        if (!allowedHost && isPrivateOrLocal(normalizedHost)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 不允许访问内网、localhost 或 metadata 地址");
        }
        if (!"https".equals(scheme) && !("http".equals(scheme) && allowHttp)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 协议不允许: " + scheme);
        }
        int port = uri.getPort() < 0 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        if (!allowedHost && !allowedPorts.contains(port)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 端口不允许: " + port);
        }
        if (!allowPrivateNetwork && !allowedHost && isPrivateIp(normalizedHost)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 不允许访问内网地址");
        }
    }

    private URI parse(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 不能为空");
        }
        try {
            return URI.create(endpoint.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 格式不正确");
        }
    }

    private boolean isAllowlisted(String host) {
        return allowlist.contains(host) || allowlist.stream()
                .filter(item -> item.startsWith("."))
                .anyMatch(host::endsWith);
    }

    private boolean isPrivateOrLocal(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || isPrivateIp(host)
                || host.startsWith("169.254.");
    }

    private boolean isPrivateIp(String host) {
        if (host.startsWith("127.")) {
            return true;
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 10
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Set<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
