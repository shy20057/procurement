package com.example.procurement.integration.purchase.source;

import com.example.procurement.integration.controller.mock.MockPurchaseOrderController;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot;
import com.example.procurement.integration.purchase.PurchaseOrderSource;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PurchaseOrderHttpSource implements PurchaseOrderSource {
    private final RestTemplate http;
    private final ApplicationContext context;
    private final String configuredBaseUrl;
    private final String sourceKey;
    private final boolean mockEnabled;
    private final String mockKey;

    public PurchaseOrderHttpSource(RestTemplateBuilder builder, ApplicationContext context,
            @Value("${integration.purchase-order.source-base-url:}") String configuredBaseUrl,
            @Value("${integration.purchase-order.source-api-key:}") String sourceKey,
            @Value("${integration.mock-sap.enabled:false}") boolean mockEnabled,
            @Value("${integration.mock-sap.api-key:}") String mockKey) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.context = context;
        this.configuredBaseUrl = configuredBaseUrl.trim();
        this.sourceKey = sourceKey;
        this.mockEnabled = mockEnabled;
        this.mockKey = mockKey;
    }

    @Override
    public Anchor anchor() {
        Anchor anchor = get(uri("anchor"), Anchor.class, false);
        if (anchor.sourceNow() == null || anchor.sourceNow().getNano() % 1000 != 0
                || anchor.maxSourceId() < 0) throw invalidResponse();
        return anchor;
    }

    @Override
    public Page baseline(long afterId, long maxId, int limit) {
        return page(uri("baseline").queryParam("afterId", afterId).queryParam("maxId", maxId)
                .queryParam("limit", limit), limit);
    }

    @Override
    public Page changes(Instant startAt, Instant endAt, Cursor after, int limit) {
        UriComponentsBuilder uri = uri("changes").queryParam("startAt", startAt).queryParam("endAt", endAt)
                .queryParam("limit", limit);
        if (after != null) uri.queryParam("afterUpdatedAt", after.updatedAt()).queryParam("afterId", after.sourceId());
        return page(uri, limit);
    }

    @Override
    public Optional<PurchaseOrderSnapshot> find(long sourceId) {
        PurchaseOrderSnapshot order = get(uri(Long.toString(sourceId)), PurchaseOrderSnapshot.class, true);
        if (order == null) return Optional.empty();
        PurchaseOrderContract.validate(order);
        if (order.sourceId() != sourceId) throw invalidResponse();
        return Optional.of(order);
    }

    private Page page(UriComponentsBuilder uri, int limit) {
        Page page = get(uri, Page.class, false);
        if (page.items() == null || page.items().size() > limit || (page.hasMore() && page.items().isEmpty())) {
            throw invalidResponse();
        }
        page.items().forEach(PurchaseOrderContract::validate);
        return page;
    }

    private UriComponentsBuilder uri(String segment) {
        String baseUrl = configuredBaseUrl;
        if (baseUrl.isBlank()) {
            if (!mockEnabled || !(context instanceof WebServerApplicationContext web) || web.getWebServer() == null) {
                throw new IntegrationProblem("SOURCE_NOT_CONFIGURED", "请配置采购订单源接口或启用Mock ERP", false, 422);
            }
            baseUrl = "http://127.0.0.1:" + web.getWebServer().getPort() + MockPurchaseOrderController.PATH;
        }
        return UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment(segment);
    }

    private <T> T get(UriComponentsBuilder builder, Class<T> type, boolean allowNotFound) {
        URI uri = builder.build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        String key = configuredBaseUrl.isBlank() ? mockKey : sourceKey;
        if (!key.isBlank()) headers.set("X-Mock-Sap-Key", key);
        try {
            T body = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
            if (body == null) throw invalidResponse();
            return body;
        } catch (HttpStatusCodeException failure) {
            int status = failure.getStatusCode().value();
            if (allowNotFound && status == 404) return null;
            throw new IntegrationProblem("SOURCE_HTTP_" + status, "采购订单源接口返回HTTP " + status,
                    status == 408 || status == 429 || status >= 500, 502);
        } catch (ResourceAccessException failure) {
            boolean timeout = isTimeout(failure);
            throw new IntegrationProblem(timeout ? "SOURCE_TIMEOUT" : "SOURCE_NETWORK_ERROR",
                    timeout ? "采购订单源接口调用超时" : "采购订单源接口网络连接失败", true, 503);
        } catch (RestClientException failure) {
            throw invalidResponse();
        }
    }

    private boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private IntegrationProblem invalidResponse() {
        return new IntegrationProblem("SOURCE_INVALID_RESPONSE", "采购订单源接口响应不符合约定", false, 502);
    }
}
