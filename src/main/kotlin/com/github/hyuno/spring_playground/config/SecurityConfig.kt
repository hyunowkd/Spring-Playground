package com.github.hyuno.spring_playground.config


import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/index.html", "/js/**", "/css/**", "/ws/**", "/topic/**").permitAll()
                    .anyRequest().permitAll()
            }
            .csrf { it.disable() } // WebSocket 테스트용으로 CSRF 비활성화
        return http.build()
    }
}