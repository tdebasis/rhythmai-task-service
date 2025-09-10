package com.havq.task.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Havq Task Service API")
                    .description("Task management microservice for Havq personal work management app")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Havq Team")
                            .url("https://havq.ai")
                    )
                    .license(
                        License()
                            .name("Private")
                            .url("https://havq.ai/license")
                    )
            )
            .addServersItem(
                Server()
                    .url("http://localhost:5002")
                    .description("Local Development Server")
            )
            .addServersItem(
                Server()
                    .url("http://localhost:3000")
                    .description("BFF Gateway (Recommended)")
            )
    }
}