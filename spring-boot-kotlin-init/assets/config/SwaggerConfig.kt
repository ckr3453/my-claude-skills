package TODO.config  // TODO: Replace with actual package

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger/OpenAPI Configuration
 * 
 * Configures API documentation with JWT authentication support
 * Access Swagger UI at: /swagger-ui.html
 */
@Configuration
class SwaggerConfig {

    @Bean
    fun openAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        
        return OpenAPI()
            .info(
                Info()
                    .title("TODO: API Title")
                    .description("TODO: API Description")
                    .version("1.0.0")
            )
            .addSecurityItem(
                SecurityRequirement().addList(securitySchemeName)
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token authentication")
                    )
            )
    }
}
