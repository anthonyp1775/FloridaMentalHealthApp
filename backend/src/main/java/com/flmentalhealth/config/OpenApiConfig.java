package com.flmentalhealth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation, generated from the code rather than maintained by
 * hand - which is NFR-15, and the reason it cannot drift out of date.
 *
 * Swagger UI: http://localhost:8080/swagger-ui.html
 *
 * The bearer security scheme below is what puts the "Authorize" button
 * in the UI. Paste a token there once and every endpoint on the page
 * becomes callable, which makes the docs a live testing tool rather
 * than a reference.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiDefinition() {
        final String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Florida Mental Health App API")
                        .version("1.0.0")
                        .description("""
                                Mental health navigation and referral system for Florida.

                                All endpoints require a bearer token except registration
                                and login. Obtain one from POST /api/auth/login, then use
                                the Authorize button above.

                                NOTE: the provider directory is synthetic sample data -
                                every organization name is prefixed "Example". No clinical
                                data of any kind is stored by this system.
                                """)
                        .contact(new Contact().name("UCI 2123 Capstone")))

                // Applies the scheme to every operation by default.
                .addSecurityItem(new SecurityRequirement().addList(schemeName))

                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
