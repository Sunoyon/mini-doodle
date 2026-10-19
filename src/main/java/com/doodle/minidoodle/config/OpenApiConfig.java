package com.doodle.minidoodle.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI api() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Mini Doodle API")
                                .version("1.0.0")
                                .description(
                                        "Local scheduling simulation. Slots are half-open"
                                                + " intervals. Participants are metadata;"
                                                + " authentication is out of scope."));
    }
}
