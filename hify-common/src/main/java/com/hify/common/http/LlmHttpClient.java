package com.hify.common.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM HTTP 调用客户端。
 *
 * <ul>
 *   <li>非流式（{@link #post}）：OkHttpClient，连接超时 5s，读超时 120s</li>
 *   <li>流式（{@link #stream}）：OkHttpClient，连接超时 5s，读超时 0（SSE 不截断）</li>
 * </ul>
 *
 * <p>所有请求记录 URL、耗时、HTTP 状态码；所有异常统一转为 {@link LlmApiException}。
 */
@Slf4j
@Component
public class LlmHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int ERROR_BODY_LIMIT = 1000;
    private static final int DEFAULT_POST_TIMEOUT_SECONDS = 120;

    private final OkHttpClient streamClient;

    public LlmHttpClient() {
        this.streamClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 同步 POST，等待完整响应后返回响应体字符串。
     *
     * @param url     目标地址
     * @param headers HTTP 头（Authorization、Content-Type 等）
     * @param body    JSON 请求体
     * @return 响应体字符串
     * @throws LlmApiException TIMEOUT / AUTH_FAILED / RATE_LIMITED / UNKNOWN
     */
    public String post(String url, Map<String, String> headers, String body) {
        return post(url, headers, body, DEFAULT_POST_TIMEOUT_SECONDS);
    }

    /**
     * 同步 POST，自定义超时时间。长任务工作流可以显式传入更长超时。
     */
    public String post(String url, Map<String, String> headers, String body, int timeoutSeconds) {
        return request("POST", url, headers, body, timeoutSeconds);
    }

    /**
     * 同步 HTTP 请求，自定义方法和超时时间。
     */
    public String request(String method, String url, Map<String, String> headers, String body, int timeoutSeconds) {
        OkHttpClient client = streamClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds + 5L, TimeUnit.SECONDS)
                .build();

        String httpMethod = method == null || method.isBlank()
                ? "GET"
                : method.toUpperCase(java.util.Locale.ROOT);
        Request.Builder builder = new Request.Builder().url(url);
        headers.forEach(builder::header);
        if ("GET".equals(httpMethod)) {
            builder.get();
        } else {
            builder.method(httpMethod, RequestBody.create(body == null ? "" : body, JSON));
        }
        Request request = builder.build();

        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            int status = response.code();
            log.info("LLM {} {} status={} elapsed={}ms", httpMethod, url, status, elapsed(start));
            if (!response.isSuccessful()) {
                String errorBody = readBody(response);
                log.warn("LLM {} {} status={} body={} elapsed={}ms",
                        httpMethod, url, status, abbreviate(errorBody), elapsed(start));
                throw classify(status, errorBody, null);
            }
            ResponseBody responseBody = response.body();
            return responseBody == null ? "" : responseBody.string();
        } catch (LlmApiException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            log.warn("LLM {} {} timeout elapsed={}ms", httpMethod, url, elapsed(start));
            throw new LlmApiException(LlmApiException.Type.TIMEOUT,
                    "LLM 请求超时: " + url, e);
        } catch (IOException e) {
            log.warn("LLM {} {} error elapsed={}ms: {}", httpMethod, url, elapsed(start), e.getMessage());
            throw new LlmApiException(LlmApiException.Type.UNKNOWN,
                    "LLM 请求异常: " + url, e);
        }
    }

    /**
     * 流式 POST，逐行回调响应内容（跳过空行）。
     *
     * <p>SSE 格式由调用方自行解析（过滤 {@code data:} 前缀、跳过 {@code [DONE]}）。
     *
     * @param url      目标地址
     * @param headers  HTTP 头
     * @param body     JSON 请求体
     * @param callback 每读到一个非空行时触发；调用方应处理异常，不要在回调中抛出受检异常
     * @throws LlmApiException TIMEOUT / AUTH_FAILED / RATE_LIMITED / UNKNOWN
     */
    public void stream(String url, Map<String, String> headers, String body, Consumer<String> callback) {
        Request.Builder builder = new Request.Builder().url(url);
        headers.forEach(builder::header);
        Request request = builder.post(RequestBody.create(body, JSON)).build();

        long start = System.currentTimeMillis();
        try (Response response = streamClient.newCall(request).execute()) {
            int status = response.code();
            if (!response.isSuccessful()) {
                String errorBody = readBody(response);
                log.warn("LLM STREAM {} status={} body={} elapsed={}ms",
                        url, status, abbreviate(errorBody), elapsed(start));
                throw classify(status, errorBody, null);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new LlmApiException(LlmApiException.Type.UNKNOWN,
                        "LLM 流式响应体为空: " + url);
            }

            long lineCount = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseBody.byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        callback.accept(line);
                        lineCount++;
                    }
                }
            }
            log.info("LLM STREAM {} status={} lines={} elapsed={}ms",
                    url, status, lineCount, elapsed(start));

        } catch (LlmApiException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            log.warn("LLM STREAM {} timeout elapsed={}ms", url, elapsed(start));
            throw new LlmApiException(LlmApiException.Type.TIMEOUT,
                    "LLM 流式请求超时: " + url, e);
        } catch (IOException e) {
            log.warn("LLM STREAM {} error elapsed={}ms: {}", url, elapsed(start), e.getMessage());
            throw new LlmApiException(LlmApiException.Type.UNKNOWN,
                    "LLM 流式请求异常: " + url, e);
        }
    }

    /**
     * 同步 GET，自定义超时，主要用于供应商连通性测试。
     *
     * @param url            目标地址
     * @param headers        HTTP 头（Authorization、x-api-key 等）
     * @param timeoutSeconds 读超时秒数（connectTimeout 固定 5s）
     * @return 响应体字符串
     * @throws LlmApiException TIMEOUT / AUTH_FAILED / RATE_LIMITED / UNKNOWN
     */
    public String get(String url, Map<String, String> headers, int timeoutSeconds) {
        return request("GET", url, headers, null, timeoutSeconds);
    }

    // ------------------------------------------------------------------ 私有方法

    private static LlmApiException classify(int statusCode, Exception cause) {
        return classify(statusCode, null, cause);
    }

    private static LlmApiException classify(int statusCode, String responseBody, Exception cause) {
        LlmApiException.Type type = switch (statusCode) {
            case 401, 403 -> LlmApiException.Type.AUTH_FAILED;
            case 429      -> LlmApiException.Type.RATE_LIMITED;
            default       -> LlmApiException.Type.UNKNOWN;
        };
        String detail = errorDetail(responseBody);
        String message = switch (type) {
            case AUTH_FAILED  -> "LLM 认证失败（HTTP " + statusCode + "），请检查 API Key";
            case RATE_LIMITED -> "LLM 请求频率超限（HTTP 429），请稍后重试";
            default           -> "LLM 请求失败（HTTP " + statusCode + "）" + detail;
        };
        return cause != null
                ? new LlmApiException(type, statusCode, message, cause)
                : new LlmApiException(type, statusCode, message);
    }

    private static String readBody(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        return responseBody == null ? "" : responseBody.string();
    }

    private static String errorDetail(String responseBody) {
        String body = abbreviate(responseBody);
        return body.isBlank() ? "" : "：" + body;
    }

    private static String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= ERROR_BODY_LIMIT) {
            return compact;
        }
        return compact.substring(0, ERROR_BODY_LIMIT) + "...";
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
