package com.complianceos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync  // Pour le provisionnement tenant async (critère CO-01 : < 2s)
public class ComplianceOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceOsApplication.class, args);
    }
}
