package com.biktech.smsgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmsGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsGatewayApplication.class, args);
    }
}
