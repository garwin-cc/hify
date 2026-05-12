package com.hify.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    /**
     * LLM 调用线程池：阻塞等待完整响应或驱动流式 SSE。
     * 满载时 CallerRunsPolicy 让调用方线程执行，不丢任务，同时产生反压。
     */
    @Bean
    @Qualifier("llmExecutor")
    public ThreadPoolExecutor llmExecutor() {
        return new ThreadPoolExecutor(
                10, 50, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadFactoryBuilder()
                        .setNameFormat("llm-%d")
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 通用异步线程池：日志异步写入、事件发布等非关键任务。
     * 文档解析和向量化必须异步执行，避免占用 Tomcat 请求线程。
     */
    @Bean
    @Qualifier("asyncExecutor")
    public ThreadPoolExecutor asyncExecutor() {
        return new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadFactoryBuilder()
                        .setNameFormat("async-%d")
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 知识库文档处理线程池：大文件解析、分块、向量化和 pgvector 写入。
     * 该任务可能持续较久，必须和通用异步任务隔离，避免拖慢健康检查和事件发布。
     */
    @Bean
    @Qualifier("knowledgeExecutor")
    public ThreadPoolExecutor knowledgeExecutor() {
        return new ThreadPoolExecutor(
                1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20),
                new ThreadFactoryBuilder()
                        .setNameFormat("knowledge-%d")
                        .setDaemon(true)
                        .build(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
