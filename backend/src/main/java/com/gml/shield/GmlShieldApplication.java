package com.gml.shield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Point d'entrée de l'application Spring Boot.
 */

@SpringBootApplication
@ConfigurationPropertiesScan
// L'audit est écrit en arrière-plan pour ne pas ajouter sa latence aux requêtes métier.
@EnableAsync
public class GmlShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmlShieldApplication.class, args);
    }
}
