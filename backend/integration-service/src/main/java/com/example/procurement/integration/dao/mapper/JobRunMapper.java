package com.example.procurement.integration.dao.mapper;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dto.domain.ClaimableRunDTO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/** 只声明数据库操作；业务决策和事务由 Service 负责。 */
public interface JobRunMapper {
JobRunDO selectById(@Param("runId") UUID runId);
    JobRunDO lockRun(@Param("runId") UUID runId);
    JobRunDO lockXxlIdentity(@Param("jobCode") String jobCode, @Param("jobId") long jobId,
            @Param("logKey") String logKey, @Param("shardIndex") int shardIndex);
    JobRunDO selectActiveResource(@Param("resourceKey") String resourceKey, @Param("excludedRun") UUID excludedRun);
    List<ClaimableRunDTO> lockLocalCandidates();
    ClaimableRunDTO lockExactCandidate(@Param("runId") UUID runId);
    List<ClaimableRunDTO> lockStaleRuns();
    int insert(JobRunDO run);
    int markRunning(@Param("runId") UUID runId, @Param("owner") String owner,
            @Param("token") long token, @Param("attempt") int attempt);
    int finishPending(@Param("runId") UUID runId, @Param("code") String code, @Param("summary") String summary);
    int markSuccess(@Param("runId") UUID runId);
    int markFailure(@Param("runId") UUID runId, @Param("status") String status,
            @Param("code") String code, @Param("summary") String summary,
            @Param("retry") boolean retry, @Param("delay") long delay);
    int cancel(@Param("runId") UUID runId);
    int markCancelled(@Param("runId") UUID runId, @Param("owner") String owner, @Param("token") long token);
    int recover(@Param("runId") UUID runId, @Param("status") String status);
    long countActive(@Param("jobCode") String jobCode);
    long countRuns(@Param("jobCode") String jobCode);
    List<JobRunDO> selectPage(@Param("jobCode") String jobCode, @Param("size") int size, @Param("offset") long offset);
    int startBaseline(@Param("runId") UUID runId, @Param("count") long count);
    int updateProgress(@Param("runId") UUID runId, @Param("phase") String phase,
            @Param("fetched") int fetched, @Param("applied") int applied, @Param("ignored") int ignored);
    int updateTotal(@Param("runId") UUID runId, @Param("remaining") long remaining);
    int startReconciliation(@Param("runId") UUID runId, @Param("count") long count);
}
