package com.example.procurement.integration.client;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.support.MasterDataCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SapMasterDataClientTest {
    private MockRestServiceServer server;
    private SapMasterDataClient client;
    @BeforeEach
    void setUp() {
        RestTemplate http=new RestTemplate();
        server=MockRestServiceServer.createServer(http);
        RestTemplateBuilder builder=mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(Duration.ofSeconds(3))).thenReturn(builder);
        when(builder.setReadTimeout(Duration.ofSeconds(15))).thenReturn(builder);
        when(builder.build()).thenReturn(http);
        client=new SapMasterDataClient(builder,mock(ApplicationContext.class),
                new MasterDataCodec(new ObjectMapper().findAndRegisterModules()),"https://sap.example/api","source-key",false,"");
    }
    @Test
    void onlyActual404MeansMissingAndPointReadKeepsSourceAuthentication() {
        server.expect(requestTo("https://sap.example/api/COST_CENTER/records/9007199254740993"))
                .andExpect(header("X-Mock-Sap-Key","source-key")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(client.find(MasterDataType.COST_CENTER,9007199254740993L)).isNull();
        server.verify();
    }
    @Test
    void forbiddenSourceResponseMustNotBeReportedAsMissing() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.find(MasterDataType.COST_CENTER,1))
                .isInstanceOfSatisfying(IntegrationProblem.class,error -> {
                    assertThat(error.code).isEqualTo("SAP_HTTP_403"); assertThat(error.retryable).isFalse();
                });
        server.verify();
    }
    @Test
    void temporarySourceFailureRemainsRetryableInsteadOfMissing() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.find(MasterDataType.COST_CENTER,1))
                .isInstanceOfSatisfying(IntegrationProblem.class,error -> {
                    assertThat(error.code).isEqualTo("SAP_HTTP_503"); assertThat(error.retryable).isTrue();
                });
        server.verify();
    }
    @Test
    void missingExtractionEndpointIsAnError() {
        server.expect(requestTo("https://sap.example/api/COST_CENTER/anchor")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.anchor(MasterDataType.COST_CENTER))
                .isInstanceOfSatisfying(IntegrationProblem.class,error -> assertThat(error.code).isEqualTo("SAP_HTTP_404"));
        server.verify();
    }
}
