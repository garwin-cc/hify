package com.hify.common.task;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class TaskExecutors {

    private TaskExecutors() {
    }

    public static ThreadPoolExecutor fixed(String nameFormat, int threads, int queueCapacity) {
        return new ThreadPoolExecutor(
                threads,
                threads,
                60L,
                TimeUnit.SECONDS,
                queueCapacity <= 0 ? new SynchronousQueue<>() : new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactoryBuilder().setNameFormat(nameFormat).setDaemon(true).build(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
