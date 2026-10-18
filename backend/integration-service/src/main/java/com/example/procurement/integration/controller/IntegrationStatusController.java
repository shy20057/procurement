package com.example.procurement.integration.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationStatusController {
    @GetMapping("/_status")
    public Map<String, Object> status() {
        return Map.of("service", "integration-service", "adapters", new String[]{"BMS", "BTS", "TMS", "SAP", "ECS"});
    }
}
