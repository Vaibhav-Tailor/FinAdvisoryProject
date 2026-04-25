package com.finadvisory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityCongif {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	    http
	        .csrf(csrf -> csrf.disable()) // disable for testing (optional)
	        .authorizeHttpRequests(auth -> auth
	            .requestMatchers("/api/admin/nav/sync").permitAll() // 👈 allow this endpoint
	            .anyRequest().authenticated()
	        );

	    return http.build();
	}
}
