package com.example.ghostbusters;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BackEndGhostBustersApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackEndGhostBustersApplication.class, args);
    }
}