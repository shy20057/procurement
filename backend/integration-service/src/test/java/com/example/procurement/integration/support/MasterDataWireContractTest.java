package com.example.procurement.integration.support;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.dto.req.CostCenterMutationReqDTO;
import com.example.procurement.integration.dto.resp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MasterDataWireContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final MasterDataCodec codec = new MasterDataCodec(mapper);

    @Test
    void sourcePageRoundTripsMicrosecondsAndIdsAboveJavascriptIntegerRange() throws Exception {
        long id=9007199254740993L;
        Instant time=Instant.parse("2026-01-01T00:00:00.123456Z");
        CostCenterDTO data=new CostCenterDTO("A100","CC_01","1000","采购部",LocalDate.of(2020,1,1),
                LocalDate.of(2099,12,31),true,false,true);
        SourceRecordDTO record=new SourceRecordDTO(MasterDataType.COST_CENTER,codec.businessKey(data),id,time,data,null);
        SourcePageRespDTO page=new SourcePageRespDTO(List.of(record),SourceCursorDTO.of(record),false);
        String json=mapper.writeValueAsString(page);
        var tree=mapper.readTree(json);
        assertThat(tree.get("items").get(0).get("sourceId").isTextual()).isTrue();
        assertThat(tree.get("nextCursor").get("sourceId").asText()).isEqualTo("9007199254740993");
        assertThat(tree.get("items").get(0).get("updatedAt").asText()).isEqualTo("2026-01-01T00:00:00.123456Z");
        assertThat(mapper.readValue(json,SourcePageRespDTO.class)).isEqualTo(page);
        SourceAnchorRespDTO anchor=new SourceAnchorRespDTO(time,id);
        assertThat(mapper.readTree(mapper.writeValueAsString(anchor)).get("maxSourceId").isTextual()).isTrue();
        CostCenterMutationReqDTO edit=new CostCenterMutationReqDTO(data,id,time);
        assertThat(mapper.readValue(mapper.writeValueAsString(edit),CostCenterMutationReqDTO.class)).isEqualTo(edit);
    }

    @Test
    void eachAvailabilityFlagIndependentlyChangesCanonicalContentHash() {
        CostCenterDTO available=new CostCenterDTO("A100","CC_01","1000","采购部",LocalDate.of(2020,1,1),LocalDate.of(2099,12,31),false,true,false);
        CostCenterDTO blocked=new CostCenterDTO("A100","CC_01","1000","采购部",available.validFrom(),available.validTo(),true,true,false);
        CostCenterDTO disabled=new CostCenterDTO("A100","CC_01","1000","采购部",available.validFrom(),available.validTo(),false,false,false);
        CostCenterDTO deleted=new CostCenterDTO("A100","CC_01","1000","采购部",available.validFrom(),available.validTo(),false,true,true);
        List<String> hashes=List.of(available,blocked,disabled,deleted).stream().map(data -> codec.hash(new SourceRecordDTO(
                MasterDataType.COST_CENTER,codec.businessKey(data),1,Instant.parse("2026-01-01T00:00:00Z"),data,null))).toList();
        assertThat(hashes).doesNotHaveDuplicates();
        assertThat(codec.businessKey(available)).isEqualTo("[\"A100\",\"CC_01\"]");
    }
}
