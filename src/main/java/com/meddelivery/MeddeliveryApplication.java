package com.meddelivery;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MeddeliveryApplication {

    public static void main(String[] args) {

        Dotenv dotenv = Dotenv.configure()
                .directory("./")              
                .ignoreIfMissing()
                .ignoreIfMalformed()          
                .load();

        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(MeddeliveryApplication.class, args);
    }
}