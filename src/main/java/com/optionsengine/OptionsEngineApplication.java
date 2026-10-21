package com.optionsengine;

import com.optionsengine.pricing.MonteCarloPricer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OptionsEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OptionsEngineApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    MonteCarloPricer monteCarloPricer() {
        return new MonteCarloPricer();
    }
}
