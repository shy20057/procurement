package com.example.procurement.integration.client;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.support.MasterDataCodec;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SapMasterDataClient {
    private final RestTemplate http;
    private final ApplicationContext context;
    private final MasterDataCodec codec;
    private final String configuredUrl;
    private final String apiKey;
    private final boolean mockEnabled;
    private final String mockKey;

    public SapMasterDataClient(RestTemplateBuilder builder, ApplicationContext context, MasterDataCodec codec,
                               @Value("${integration.sap.base-url:}") String configuredUrl,
                               @Value("${integration.sap.api-key:}") String apiKey,
                               @Value("${integration.mock-sap.enabled:false}") boolean mockEnabled,
                               @Value("${integration.mock-sap.api-key:}") String mockKey) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(15)).build(); // 实际发请求,超时设置:连接 3s、读取 15s
        this.context = context; // 关键用途:mock 模式下动态拿本服务自己的端口
        this.codec = codec; // 校验响应非空
        this.configuredUrl = configuredUrl; // SAP 真实地址,来自 integration.sap.base-url,默认空
        this.apiKey = apiKey; // 真实 SAP 的鉴权 key
        this.mockEnabled = mockEnabled; // 是否启用本地模拟源
        this.mockKey = mockKey; // 	Mock 的鉴权 key
    }

    // 先 snapshot() 建一份快照,拿到快照 id
    public SnapshotRespDTO snapshot(MasterDataType type) {
        return call(type, "/snapshots", HttpMethod.POST, SnapshotRespDTO.class);
    }

    // 用 snapshotPage() 带游标(after)分页把基线拉完
    public SourcePageRespDTO snapshotPage(MasterDataType type, UUID id, long after, int size) {
        return call(type, "/snapshots/" + id + "?after=" + after + "&limit=" + size, HttpMethod.GET, SourcePageRespDTO.class);
    }

    // 之后用 changes() 按游标(after/upper)拉增量
    public SourcePageRespDTO changes(MasterDataType type, String after, String upper, int size) {
        String path = UriComponentsBuilder.fromPath("/changes").queryParam("after", after)
                .queryParam("limit", size).queryParamIfPresent("upper", java.util.Optional.ofNullable(upper))
                .build().encode().toUriString();
        return call(type, path, HttpMethod.GET, SourcePageRespDTO.class);
    }

    private <T> T call(MasterDataType type, String path, HttpMethod method, Class<T> responseType) {
        String base = configuredUrl;
        if (base.isBlank()) {
            // 没配置 SAP 地址:
            //   - 若 mock 也没开启 → 直接抛错 "SAP_NOT_CONFIGURED"(不可重试,HTTP 422)
            //   - 若 mock 开启 → 指向本服务自己的 mock-sap 接口
            if (!mockEnabled || !(context instanceof WebServerApplicationContext web) || web.getWebServer() == null)
                throw new IntegrationProblem("SAP_NOT_CONFIGURED", "请配置SAP数据接口或启用本地模拟源", false, 422);
            base = "http://127.0.0.1:" + web.getWebServer().getPort() + "/api/integration/mock-sap";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Mock-Sap-Key", configuredUrl.isBlank() ? mockKey : apiKey);
        try {
            T body = http.exchange(base.replaceAll("/$", "") + "/" + type.name() + path,
                    method, new HttpEntity<>(headers), responseType).getBody();
            codec.require(body != null, "SAP返回空响应");
            return body;
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String code = status == 410 ? "SNAPSHOT_EXPIRED" : "SAP_HTTP_" + status;
            throw new IntegrationProblem(code, status == 410 ? "SAP快照已过期，请重建未完成基线" : "SAP接口返回HTTP " + status,
                    status == 429 || status >= 500, 502);
        } catch (ResourceAccessException e) {
            throw new IntegrationProblem("SAP_NETWORK_ERROR", "SAP连接失败或读取超时", true, 502);
        }
    }
}
