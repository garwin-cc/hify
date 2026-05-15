package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class McpEndpointGuard {

    private final boolean allowHttp;
    private final boolean allowPrivateNetwork;
    private final Set<String> allowlist;
    private final Set<Integer> allowedPorts;
    private final Set<Integer> blockedPorts;
    private final List<CidrBlock> privateCidrAllowlist;
    private final boolean dnsResolveEnabled;
    private final Function<String, List<InetAddress>> dnsResolver;

    public McpEndpointGuard() {
        this(false, false, "", "443");
    }

    public McpEndpointGuard(boolean allowHttp, boolean allowPrivateNetwork, String allowlist, String allowedPorts) {
        this(allowHttp, allowPrivateNetwork, allowlist, allowedPorts,
                "22,3306,5432,6379,9200,9300,27017,11211", "", false, McpEndpointGuard::resolveDns);
    }

    public McpEndpointGuard(@Value("${hify.mcp.security.allow-http:false}") boolean allowHttp,
                            @Value("${hify.mcp.security.allow-private-network:false}") boolean allowPrivateNetwork,
                            @Value("${hify.mcp.security.endpoint-allowlist:}") String allowlist,
                            @Value("${hify.mcp.security.allowed-ports:443}") String allowedPorts,
                            @Value("${hify.mcp.security.blocked-ports:22,3306,5432,6379,9200,9300,27017,11211}") String blockedPorts,
                            @Value("${hify.mcp.security.private-cidr-allowlist:}") String privateCidrAllowlist,
                            @Value("${hify.mcp.security.dns-resolve-enabled:false}") boolean dnsResolveEnabled) {
        this(allowHttp, allowPrivateNetwork, allowlist, allowedPorts, blockedPorts, privateCidrAllowlist,
                dnsResolveEnabled, McpEndpointGuard::resolveDns);
    }

    McpEndpointGuard(boolean allowHttp,
                     boolean allowPrivateNetwork,
                     String allowlist,
                     String allowedPorts,
                     String blockedPorts,
                     String privateCidrAllowlist,
                     boolean dnsResolveEnabled,
                     Function<String, List<InetAddress>> dnsResolver) {
        this.allowHttp = allowHttp;
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.allowlist = split(allowlist);
        this.allowedPorts = splitPorts(allowedPorts);
        this.blockedPorts = splitPorts(blockedPorts);
        this.privateCidrAllowlist = split(privateCidrAllowlist).stream()
                .map(CidrBlock::parse)
                .toList();
        this.dnsResolveEnabled = dnsResolveEnabled;
        this.dnsResolver = dnsResolver;
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
        validateHostAddress(normalizedHost, allowedHost);
        if (dnsResolveEnabled && !isIpLiteral(normalizedHost)) {
            for (InetAddress address : dnsResolver.apply(normalizedHost)) {
                validateResolvedAddress(address, allowedHost);
            }
        }
        if (!"https".equals(scheme) && !("http".equals(scheme) && allowHttp)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 协议不允许: " + scheme);
        }
        int port = uri.getPort() < 0 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        if (blockedPorts.contains(port)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 端口不允许: " + port);
        }
        if (!allowedPorts.isEmpty() && !allowedPorts.contains(port)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 端口不允许: " + port);
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

    private void validateHostAddress(String host, boolean allowedHost) {
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            rejectPrivateUnlessAllowed(null, allowedHost);
            return;
        }
        InetAddress address = parseIpLiteral(host);
        if (address != null) {
            validateResolvedAddress(address, allowedHost);
        }
    }

    private void validateResolvedAddress(InetAddress address, boolean allowedHost) {
        if (isMetadataAddress(address)) {
            rejectPrivateUnlessAllowed(address, allowedHost);
            return;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()) {
            rejectPrivateUnlessAllowed(address, allowedHost);
        }
    }

    private void rejectPrivateUnlessAllowed(InetAddress address, boolean allowedHost) {
        if (allowedHost || allowPrivateNetwork || isPrivateCidrAllowed(address)) {
            return;
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint 不允许访问内网、localhost 或 metadata 地址");
    }

    private boolean isPrivateCidrAllowed(InetAddress address) {
        if (address == null || privateCidrAllowlist.isEmpty()) {
            return false;
        }
        return privateCidrAllowlist.stream().anyMatch(cidr -> cidr.matches(address));
    }

    private static boolean isMetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && Byte.toUnsignedInt(bytes[0]) == 169
                && Byte.toUnsignedInt(bytes[1]) == 254
                && Byte.toUnsignedInt(bytes[2]) == 169
                && Byte.toUnsignedInt(bytes[3]) == 254;
    }

    private static boolean isIpLiteral(String host) {
        return parseIpLiteral(host) != null;
    }

    private static InetAddress parseIpLiteral(String host) {
        try {
            if (host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')
                    || host.contains(":")) {
                return InetAddress.getByName(host);
            }
            return null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static List<InetAddress> resolveDns(String host) {
        try {
            return List.of(InetAddress.getAllByName(host));
        } catch (UnknownHostException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint DNS 解析失败: " + host);
        }
    }

    private static Set<Integer> splitPorts(String value) {
        return split(value).stream()
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
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

    private record CidrBlock(long network, int prefixLength) {

        private static CidrBlock parse(String value) {
            String[] parts = value.split("/");
            if (parts.length != 2) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint CIDR 配置不正确: " + value);
            }
            InetAddress address = parseIpLiteral(parts[0]);
            if (address == null || address.getAddress().length != 4) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint CIDR 仅支持 IPv4: " + value);
            }
            try {
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > 32) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint CIDR 前缀不正确: " + value);
                }
                long mask = prefix == 0 ? 0 : 0xFFFF_FFFFL << (32 - prefix) & 0xFFFF_FFFFL;
                return new CidrBlock(toLong(address) & mask, prefix);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "MCP endpoint CIDR 前缀不正确: " + value);
            }
        }

        private boolean matches(InetAddress address) {
            if (address.getAddress().length != 4) {
                return false;
            }
            long mask = prefixLength == 0 ? 0 : 0xFFFF_FFFFL << (32 - prefixLength) & 0xFFFF_FFFFL;
            return (toLong(address) & mask) == network;
        }

        private static long toLong(InetAddress address) {
            byte[] bytes = address.getAddress();
            long result = 0;
            for (byte value : bytes) {
                result = (result << 8) | Byte.toUnsignedInt(value);
            }
            return result & 0xFFFF_FFFFL;
        }
    }
}
