package TODO.config  // TODO: Replace with actual package

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.*

/**
 * Spring Security Configuration
 * 
 * Configures:
 * - JWT authentication
 * - CORS
 * - Authorization rules
 * - Password encoding
 * - JPA Auditing
 */
@Configuration
@EnableWebSecurity
@EnableJpaAuditing
class SecurityConfig(
    // TODO: Inject JwtUtil and exception handlers
    // private val jwtUtil: JwtUtil,
    // private val jwtAccessDeniedHandler: JwtAccessDeniedHandler,
    // private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
) : AuditorAware<String> {

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    @Throws(Exception::class)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { corsConfigurationSource() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // Public endpoints
                it.requestMatchers(*ANONYMOUS_LIST).permitAll()

                // Admin endpoints
                it.requestMatchers(*ADMIN_LIST).hasAuthority("ROLE_ADMIN")

                // User endpoints
                it.requestMatchers(*USER_LIST).hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")

                // All other requests require authentication
                it.anyRequest().authenticated()
            }
            .anonymous { it.principal("SYSTEM") }
            // TODO: Add JWT filter
            // .with(JwtSecurityConfig(jwtUtil)) {}
            // TODO: Add exception handlers
            // .exceptionHandling {
            //     it.accessDeniedHandler(jwtAccessDeniedHandler)
            //     it.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            // }

        return http.build()
    }

    override fun getCurrentAuditor(): Optional<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication == null || !authentication.isAuthenticated) {
            Optional.empty()
        } else {
            Optional.of(authentication.name)
        }
    }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val corsConfiguration = CorsConfiguration()
        corsConfiguration.addAllowedOriginPattern("*")
        corsConfiguration.addAllowedHeader("*")
        corsConfiguration.addAllowedMethod("*")
        corsConfiguration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfiguration)
        return source
    }

    companion object {
        private val ANONYMOUS_LIST = arrayOf(
            "/favicon.ico",
            "/error",
            "/v2/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/actuator/**",
            "/login",
            "/register",
            // TODO: Add more public endpoints
        )

        private val ADMIN_LIST = arrayOf(
            "/admin/**",
            // TODO: Add admin-only endpoints
        )

        private val USER_LIST = arrayOf(
            "/api/**",
            // TODO: Add user endpoints
        )
    }
}
