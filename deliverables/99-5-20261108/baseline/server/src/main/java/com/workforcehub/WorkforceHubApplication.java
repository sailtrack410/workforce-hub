package com.workforcehub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.workforcehub.**.mapper")
@EnableAsync
@EnableScheduling
public class WorkforceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkforceHubApplication.class, args);
    }
}
