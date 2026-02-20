package com.training.ai.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {
    
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
    
    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(
                5, TimeUnit.SECONDS,
                30, TimeUnit.SECONDS,
                true
        );
    }
    
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }
}
