package com.norseintel.cloud.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(API_KEY))
                .components(new Components()
                        .addSecuritySchemes(API_KEY,
                                new SecurityScheme()
                                        .name("X-API-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Authentication using API Key in header")))
                .info(new Info()
                        .title("NorseIntel Cloud - Forensic Analysis API")
                        .description("API for image and file forensic analysis")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("NorseIntel Cloud Team")
                                .email("support@norseintel.cloud"))
                        .license(new License()
                                .name("Private")
                                .url("https://norseintel.cloud/license")));
    }
}