package com.biktech.smsgateway.config;

import com.biktech.smsgateway.common.ApiResponse;
import com.biktech.smsgateway.common.ErrorCode;
import com.biktech.smsgateway.feature.caller.CallerKind;
import com.biktech.smsgateway.feature.caller.CallerService;
import com.biktech.smsgateway.security.ApiKeyAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;

/** The API signs in by key and keeps no session; the console signs in with a password. */
@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, CallerService callers) throws Exception {
        http.securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/phone/**").hasRole(CallerKind.PHONE.name())
                .requestMatchers("/api/v1/messages", "/api/v1/messages/**").hasRole(CallerKind.CLIENT.name())
                .anyRequest().denyAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> writeError(response, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((request, response, e) -> writeError(response, ErrorCode.FORBIDDEN)))
            .addFilterBefore(new ApiKeyAuthenticationFilter(callers), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain consoleChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/assets/**", "/phone/**", "/actuator/health", "/error").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).failureUrl("/login?failed").permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?signedOut"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** The one console account; with no password set nobody can sign in. */
    @Bean
    public UserDetailsService consoleAccount(GatewayProperties properties, PasswordEncoder encoder) {
        GatewayProperties.Admin admin = properties.getAdmin();
        if (!StringUtils.hasText(admin.getPassword())) {
            log.warn("Console sign-in is off: set ADMIN_PASSWORD");
            return new InMemoryUserDetailsManager();
        }
        String stored = admin.getPassword().startsWith("{") ? admin.getPassword() : encoder.encode(admin.getPassword());
        return new InMemoryUserDetailsManager(User.withUsername(admin.getUsername()).password(stored).roles("ADMIN").build());
    }

    private static void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ApiResponse.failJson(code));
    }
}
