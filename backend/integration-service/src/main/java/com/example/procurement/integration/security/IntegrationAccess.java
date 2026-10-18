package com.example.procurement.integration.security;

import com.example.procurement.integration.exception.IntegrationProblem;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 管理操作的门禁
 * */
@Component
public class IntegrationAccess {
    private final boolean enabled;
    private final String adminKey;
    public IntegrationAccess(@Value("${integration.management.enabled:false}") boolean enabled,
                             @Value("${integration.management.admin-key:}") String adminKey) {
        this.enabled = enabled; this.adminKey = adminKey;
    }
    public boolean enabled() { return enabled; }
    public void requireMutation(HttpServletRequest request) {
        if (!enabled) throw new IntegrationProblem("MANAGEMENT_DISABLED", "当前环境未启用集成管理写操作", false, 403);
        String actual = request.getHeader("X-Integration-Admin-Key");
        if (!adminKey.isBlank() && (actual == null || !MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))) {
            throw new IntegrationProblem("MANAGEMENT_FORBIDDEN", "缺少有效的集成管理密钥", false, 403);
        }
    }
}
