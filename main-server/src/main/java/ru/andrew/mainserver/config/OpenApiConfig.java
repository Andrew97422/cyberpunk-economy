package ru.andrew.mainserver.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI gameCoreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Game Core API")
                        .description("Core backend API for local cyberpunk intranet")
                        .version("v1.0.0"))
                .externalDocs(new ExternalDocumentation()
                        .description("Internal project docs")
                        .url("http://localhost:8080/api/docs/swagger"))
                // 1. Регистрируем схему авторизации (описываем, как передавать токен)
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Введите ваш JWT токен в формате: Bearer <токен>")))
                // 2. Применяем авторизацию глобально ко всем эндпоинтам API
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}