package com.example.mcpgateway.common.config;

import com.example.mcpgateway.identity.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt)throws Exception{
        return http.csrf(csrf->csrf.disable())
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth
                        .requestMatchers("/actuator/health","/api/auth/login","/api/auth/refresh",
                                "/api-docs/**","/swagger-ui/**","/v3/api-docs/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/system/status").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .requestMatchers("/api/users/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/api/http-tools/**","/api/servers/**","/api/network-allowlist/**","/api/ai-config/**","/api/ai-chat/**").hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
