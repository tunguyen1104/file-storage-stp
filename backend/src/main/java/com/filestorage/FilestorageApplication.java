package com.filestorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FilestorageApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilestorageApplication.class, args);
    }
}
