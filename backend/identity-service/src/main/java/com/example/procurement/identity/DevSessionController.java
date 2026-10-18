package com.example.procurement.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/auth")
class DevSessionController {
    private static final Map<String, List<String>> USERS = Map.of(
            "buyer", List.of("supplier:read", "supplier:create"),
            "manager", List.of("supplier:read", "supplier:approve"),
            "auditor", List.of("supplier:read", "audit:read"));

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    Session login(@Valid @RequestBody Login request) {
        var permissions = USERS.get(request.username());
        if (permissions == null || !"demo123".equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // Phase 1 dev session only. Replace with signed JWT before any non-local deployment.
        return new Session("dev-" + request.username(), request.username(), permissions, 3600);
    }

    record Login(@NotBlank String username, @NotBlank String password) {}
    record Session(String accessToken, String username, List<String> permissions, int expiresIn) {}
}

