package com.rhythmai.task.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    
    override fun addCorsMappings(registry: CorsRegistry) {
        // Note: This service is internal-only and should only receive requests from BFF
        // CORS is configured defensively but BFF should handle external CORS
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:3000", "https://*.rhythmai.work")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}