package com.example.procurement.settlement;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@SpringBootApplication
public class SettlementServiceApplication { public static void main(String[] args){SpringApplication.run(SettlementServiceApplication.class,args);} }
@RestController @RequestMapping("/api/v1/invoices")
class SettlementStatusController { @GetMapping("/_status") Map<String,String> status(){return Map.of("service","settlement-service","phase","scaffold");} }

