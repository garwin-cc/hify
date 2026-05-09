package com.hify.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class LlmHttpConfig {

    /**
     * 非流式 LLM 调用客户端：有 readTimeout，适用于等待完整响应的场景。
     */
    @Bean("standardLlmClient")
    public OkHttpClient standardLlmClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES))
                .addInterceptor(buildLoggingInterceptor())
                .build();
    }

    /**
     * 流式 SSE 调用客户端：readTimeout=0，SSE 长连接不能有读超时。
     */
    @Bean("streamLlmClient")
    public OkHttpClient streamLlmClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(buildLoggingInterceptor())
                .build();
    }

    private HttpLoggingInterceptor buildLoggingInterceptor() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return interceptor;
    }
}
