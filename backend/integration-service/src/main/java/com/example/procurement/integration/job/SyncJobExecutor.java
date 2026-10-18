package com.example.procurement.integration.job;

import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.JobExecutionCoordinator;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
* 本地调度器
* 这是一个基于 Spring @Scheduled 的本地调度器,用四个定时任务驱动整个非 XXL 触发的任务流转:
* */
@Component
public class SyncJobExecutor {
    private static final Logger log = LoggerFactory.getLogger(SyncJobExecutor.class);
    private final SyncJobService jobs;
    private final SyncLeaseService leases;
    private final SyncAlertService alerts;
    private final JobExecutionCoordinator coordinator;
    private final ExecutorService workers;
    private final String node = "manual:" + UUID.randomUUID();
    private final Semaphore capacity = new Semaphore(2);
    private volatile boolean ready;

    public SyncJobExecutor(SyncJobService jobs, JobExecutionCoordinator coordinator,
                           SyncLeaseService leases, SyncAlertService alerts,
                           @Qualifier("masterDataWorkers") ExecutorService workers) {
        this.jobs = jobs;
        this.leases = leases;
        this.alerts = alerts;
        this.coordinator = coordinator;
        this.workers = workers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        ready = true;
    }

    /*
    * dispatch():每秒尝试从 SyncJobService.claim() 领取 PENDING/待重试的任务,交给一个容量为 2 的信号量(Semaphore)控制的工作线程池执行。
    * 信号量限制同时只跑 2 个任务。
    *  */
    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        if (!ready) return;
        while (capacity.tryAcquire()) {
            JobContext context = null;
            try {
                context = jobs.claim(node);
                if (context == null) {
                    capacity.release();
                    return;
                }
                JobContext claimed = context;
                workers.execute(() -> {
                    try {
                        coordinator.executeClaimed(claimed, RetryOwner.LOCAL);
                    } finally {
                        capacity.release();
                    }
                });
            } catch (RuntimeException failure) {
                if (context != null) leases.release(context);
                capacity.release();
                log.error("领取主数据同步任务失败", failure);
                return;
            }
        }
    }

    /*
    * heartbeats():对正在执行的任务(coordinator.activeContexts())定期 renew() 续租,证明自己还活着。
    * */
    @Scheduled(fixedDelay = 15000)
    public void heartbeats() {
        if (!ready) return;
        for (JobContext context : coordinator.activeContexts()) {
            try {
                if (!leases.renew(context)) log.warn("任务已丢失租约 runId={}", context.runId());
            } catch (Exception failure) {
                log.error("租约续期失败 runId={}", context.runId(), failure);
            }
        }
    }

    /*
    * recovery():调用 recoverExpired() 找回那些"执行者已失联"的任务(租约过期、owner 变了、token 变了),让它们重新进入队列或标记失败。
    * */
    @Scheduled(fixedDelay = 15000, initialDelay = 5000)
    public void recovery() {
        if (ready) jobs.recoverExpired();
    }

    /*
    * staleAlerts():检查成本中心和汇率是否超过约定同步时效，产生或更新滞后告警。
    * */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void staleAlerts() {
        if (ready) alerts.staleAlerts();
    }
}
