package com.example.procurement.integration.security;

import com.example.procurement.integration.exception.IntegrationProblem;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "integration.mock-sap.enabled", havingValue = "true")
public class MockSapAccess {
    private final String key;
    public MockSapAccess(@Value("${integration.mock-sap.api-key:}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("模拟SAP必须配置api-key");
        this.key = key;
    }
    public void requireSource(HttpServletRequest request) {
        String actual = request.getHeader("X-Mock-Sap-Key");
        if (actual == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new IntegrationProblem("SOURCE_FORBIDDEN", "源接口认证失败", false, 403);
        }
    }
}
