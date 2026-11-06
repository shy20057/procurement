package com.example.procurement.integration.mock.dao.mapper;
import com.example.procurement.integration.mock.dao.entity.MockMasterRecordDO;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
/** Bound only to the independent mock SAP SqlSession. */
public interface MockSapMapper {
    SourceAnchorRespDTO anchor(@Param("type") String type);
    long count(@Param("type") String type);
    List<MockMasterRecordDO> selectPage(@Param("type") String type, @Param("size") int size, @Param("offset") long offset);
    MockMasterRecordDO find(@Param("type") String type, @Param("id") long id);
    List<MockMasterRecordDO> fullPage(@Param("type") String type, @Param("after") long after, @Param("max") long max, @Param("limit") int limit);
    List<MockMasterRecordDO> changes(@Param("type") String type, @Param("from") Instant from, @Param("to") Instant to,
            @Param("afterTime") Instant afterTime, @Param("afterId") long afterId, @Param("limit") int limit);
    int insert(@Param("row") MockMasterRecordDO row);
    int update(@Param("row") MockMasterRecordDO row, @Param("expected") Instant expected);
    int seed(@Param("row") MockMasterRecordDO row);
}
