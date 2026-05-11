package com.capstoneecho.echo_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.capstoneecho.echo_back")
public class EchoBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoBackApplication.class, args);
    }

}
