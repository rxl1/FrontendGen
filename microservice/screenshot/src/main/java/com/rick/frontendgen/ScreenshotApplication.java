package com.rick.frontendgen;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class ScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScreenshotApplication.class, args);
    }
}
