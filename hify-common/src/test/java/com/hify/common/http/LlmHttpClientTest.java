package com.hify.common.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmHttpClientTest {

    @Test
    void proxySelectorBypassesPrivateProviderAddresses() {
        ProxySelector selector = LlmHttpClient.privateAddressBypassProxySelector(
                fixedProxySelector(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890))));

        assertThat(selector.select(URI.create("http://10.0.0.19:11434/api/tags")))
                .containsExactly(Proxy.NO_PROXY);
        assertThat(selector.select(URI.create("http://192.168.1.20:11434/api/tags")))
                .containsExactly(Proxy.NO_PROXY);
        assertThat(selector.select(URI.create("http://localhost:11434/api/tags")))
                .containsExactly(Proxy.NO_PROXY);
    }

    @Test
    void proxySelectorDelegatesPublicAddressesToSystemProxy() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890));
        ProxySelector selector = LlmHttpClient.privateAddressBypassProxySelector(fixedProxySelector(proxy));

        assertThat(selector.select(URI.create("https://api.deepseek.com/v1/models")))
                .containsExactly(proxy);
    }

    private static ProxySelector fixedProxySelector(Proxy proxy) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // test selector does not record failures
            }
        };
    }
}
