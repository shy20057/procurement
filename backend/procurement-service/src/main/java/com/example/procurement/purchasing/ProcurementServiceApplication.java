package com.example.procurement.purchasing;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@SpringBootApplication
public class ProcurementServiceApplication { public static void main(String[] args){SpringApplication.run(ProcurementServiceApplication.class,args);} }
@RestController @RequestMapping("/api/v1/purchase-requisitions")
class ProcurementStatusController { @GetMapping("/_status") Map<String,String> status(){return Map.of("service","procurement-service","phase","scaffold");} }

