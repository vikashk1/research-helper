package com.epam.research;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ResearchHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchHelperApplication.class, args);
    }
}
