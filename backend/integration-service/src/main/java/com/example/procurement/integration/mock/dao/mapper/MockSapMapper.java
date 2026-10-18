package com.example.procurement.integration.mock.dao.mapper;

import com.example.procurement.integration.mock.dao.entity.MockChangeDO;
import com.example.procurement.integration.mock.dao.entity.MockMasterRecordDO;
import com.example.procurement.integration.mock.dao.entity.MockSnapshotDO;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;

/** 仅绑定独立模拟源 SqlSession，不能进入 SRM 业务 MapperScan。 */
public interface MockSapMapper {
    long lockStream(@Param("type") String type);
    long currentSequence(@Param("type") String type);
    Long selectVersion(@Param("type") String type, @Param("key") String key);
    int upsertRecord(@Param("type") String type, @Param("key") String key, @Param("version") long version, @Param("json") String json);
    int insertChange(@Param("type") String type, @Param("sequence") long sequence, @Param("json") String json);
    int updateSequence(@Param("type") String type, @Param("sequence") long sequence);
    long count(@Param("type") String type);
    List<MockMasterRecordDO> selectPage(@Param("type") String type, @Param("size") int size, @Param("offset") long offset);
    List<MockChangeDO> selectChanges(@Param("type") String type, @Param("after") long after, @Param("upper") long upper, @Param("limit") int limit);
    int insertSnapshot(@Param("id") UUID id, @Param("type") String type, @Param("cursor") String cursor, @Param("count") long count);
    int copySnapshot(@Param("id") UUID id, @Param("type") String type);
    MockSnapshotDO selectSnapshot(@Param("id") UUID id, @Param("type") String type);
    List<MockChangeDO> selectSnapshotPage(@Param("id") UUID id, @Param("after") long after, @Param("limit") int limit);
    int setUnavailable(@Param("type") String type, @Param("unavailable") boolean unavailable);
    Boolean isUnavailable(@Param("type") String type);
}
