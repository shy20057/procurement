package com.example.procurement.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SyncSettings {
    public final int pageSize,writeBatchSize,maxAttempts,leaseSeconds;
    public final int settleDelaySeconds,overlapSeconds,resumeRewindSeconds;
    @Autowired
    public SyncSettings(@Value("${integration.sync.page-size:500}") int pageSize,
            @Value("${integration.sync.write-batch-size:200}") int writeBatchSize,
            @Value("${integration.sync.max-attempts:5}") int maxAttempts,
            @Value("${integration.sync.lease-seconds:60}") int leaseSeconds,
            @Value("${integration.sync.settle-delay-seconds:120}") int settleDelaySeconds,
            @Value("${integration.sync.overlap-seconds:300}") int overlapSeconds,
            @Value("${integration.sync.resume-rewind-seconds:1800}") int resumeRewindSeconds) {
        if(pageSize<1||pageSize>1000||writeBatchSize<1||writeBatchSize>500||
                maxAttempts<1||maxAttempts>10||leaseSeconds<45||settleDelaySeconds<0||
                overlapSeconds<0||resumeRewindSeconds<60) throw new IllegalArgumentException("同步配置超出允许范围");
        this.pageSize=pageSize;this.writeBatchSize=writeBatchSize;this.maxAttempts=maxAttempts;
        this.leaseSeconds=leaseSeconds;this.settleDelaySeconds=settleDelaySeconds;
        this.overlapSeconds=overlapSeconds;this.resumeRewindSeconds=resumeRewindSeconds;
    }
    public SyncSettings(int pageSize,int writeBatchSize,int maxAttempts,int leaseSeconds) {
        this(pageSize,writeBatchSize,maxAttempts,leaseSeconds,120,300,1800);
    }
}
