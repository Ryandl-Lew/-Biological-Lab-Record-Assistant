package com.bionote.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(name = "bionote.dev-seed-enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {
    private final DemoDataService demoDataService;

    public DevDataSeeder(DemoDataService demoDataService) { this.demoDataService = demoDataService; }

    @Override
    public void run(ApplicationArguments args) { demoDataService.seed(); }
}
