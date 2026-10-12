package com.flmentalhealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Florida Mental Health App - entry point.
 *
 * Component scanning starts from THIS package (com.flmentalhealth), so
 * every @Component / @Service / @Configuration must live at or below it.
 */
@SpringBootApplication
public class FloridaMentalHealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(FloridaMentalHealthApplication.class, args);
    }
}
