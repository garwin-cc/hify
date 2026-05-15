package com.hify.model.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookProviderHealthAlertNotifier implements ProviderHealthAlertNotifier {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final ProviderHealthAlertProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public void notify(ProviderHealthAlertEvent event) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getWebhookUrl())) {
            return;
        }
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                    .build();
            String body = objectMapper.writeValueAsString(Map.of(
                    "event", "PROVIDER_DOWN",
                    "providerId", event.providerId(),
                    "providerName", value(event.providerName()),
                    "providerType", value(event.providerType()),
                    "status", value(event.status()),
                    "failCount", event.failCount() == null ? 0 : event.failCount(),
                    "latencyMs", event.latencyMs() == null ? 0 : event.latencyMs(),
                    "errorMessage", value(event.errorMessage())
            ));
            Request request = new Request.Builder()
                    .url(properties.getWebhookUrl())
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("provider health alert webhook failed providerId={} statusCode={}",
                            event.providerId(), response.code());
                }
            }
        } catch (Exception e) {
            log.warn("provider health alert webhook error providerId={} message={}",
                    event.providerId(), e.getMessage());
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
