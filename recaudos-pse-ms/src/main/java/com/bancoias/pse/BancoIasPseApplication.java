package com.bancoias.pse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BancoIasPseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoIasPseApplication.class, args);
    }
}
