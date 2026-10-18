package com.example.procurement.integration.config;

import com.example.procurement.integration.job.SyncJobExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 创建一个固定两个工作线程的线程池，交给spring容器，Bean名称为masterDataWorkers
* SyncJobExecutor 注入它，执行人工提交的同步任务
* 应用关闭时调用shutdown（），请求停止线程池
* */
@Configuration
public class SyncInfrastructure {
    @Bean(name = "masterDataWorkers", destroyMethod = "shutdownNow")
    ExecutorService masterDataWorkers() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "master-data-sync");
            thread.setDaemon(true);
            return thread;
        });
    }
}
