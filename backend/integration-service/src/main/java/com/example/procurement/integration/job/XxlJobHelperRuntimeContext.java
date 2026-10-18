package com.example.procurement.integration.job;

import com.xxl.job.core.context.XxlJobHelper;
import org.springframework.stereotype.Component;

/*
* XxlRuntimeContext 的唯一实现,内部全部委托给 XXL-Job 官方提供的静态工具类 XxlJobHelper。XxlJobHelper
*  用 ThreadLocal 保存当前线程的调度上下文,所以这些方法只能在 @XxlJob 方法里调用。
* */
@Component
public class XxlJobHelperRuntimeContext implements XxlRuntimeContext {
    public long jobId() { return XxlJobHelper.getJobId(); }
    public String jobLogFileName() { return XxlJobHelper.getJobLogFileName(); }
    public int shardIndex() { return XxlJobHelper.getShardIndex(); }
    public int shardTotal() { return XxlJobHelper.getShardTotal(); }
    public void log(String message) { XxlJobHelper.log(message); }
    public void success(String message) { XxlJobHelper.handleSuccess(message); }
    public void fail(String message) { XxlJobHelper.handleFail(message); }
}
