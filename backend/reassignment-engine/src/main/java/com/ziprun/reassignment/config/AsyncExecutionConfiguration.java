package com.ziprun.reassignment.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

@Configuration
public class AsyncExecutionConfiguration {

    @Bean(name = "virtualThreadExecutorService", destroyMethod = "close")
    public ExecutorService virtualThreadExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "virtualThreadTaskExecutor")
    public AsyncTaskExecutor virtualThreadTaskExecutor(
            @Qualifier("virtualThreadExecutorService") ExecutorService executorService) {
        return new TaskExecutorAdapter(executorService);
    }
}
