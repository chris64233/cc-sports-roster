package com.chris64233.cc.sportsroster.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
