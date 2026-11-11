package itmo.goto.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.StaticHeadersWriter
import tools.jackson.databind.json.JsonMapper

/**
 * Protects the API with application access tokens sent as Bearer credentials.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity, mapper: JsonMapper): SecurityFilterChain {
        val challenge = BearerChallenge(mapper)
        return http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers {
                it.cacheControl { control ->
                    control.disable()
                }.addHeaderWriter(StaticHeadersWriter("Cache-Control", "no-store"))
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/telegram").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/bot/webhook").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt { }.authenticationEntryPoint(challenge) }
            .exceptionHandling { it.authenticationEntryPoint(challenge) }
            .build()
    }

    @Bean
    fun identity(): Identity = TokenIdentity(SecurityContextHolder::getContext)
}
