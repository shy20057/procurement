package com.example.procurement.integration.job;

/*
* 一个抽象接口,把对 XXL-Job 运行时的依赖(拿 jobId、写日志、报告成功/失败)隔离出来。
* 这样业务代码不直接依赖 XXL-Job 的静态类,便于测试和解耦。
* */
public interface XxlRuntimeContext {
    long jobId();
    String jobLogFileName();
    int shardIndex();
    int shardTotal();
    void log(String message);
    void success(String message);
    void fail(String message);
}
