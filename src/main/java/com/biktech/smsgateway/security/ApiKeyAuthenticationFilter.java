package com.biktech.smsgateway.security;

import com.biktech.smsgateway.feature.caller.CallerKind;
import com.biktech.smsgateway.feature.caller.CallerPrincipal;
import com.biktech.smsgateway.feature.caller.CallerService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Signs in an app by X-Api-Key, or a phone by X-Device-Key. */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String DEVICE_KEY_HEADER = "X-Device-Key";

    private final CallerService callers;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String deviceKey = request.getHeader(DEVICE_KEY_HEADER);
        String apiKey = request.getHeader(API_KEY_HEADER);
        Optional<CallerPrincipal> caller = Optional.empty();
        if (StringUtils.hasText(deviceKey)) {
            caller = callers.authenticate(deviceKey.trim(), CallerKind.PHONE);
        } else if (StringUtils.hasText(apiKey)) {
            caller = callers.authenticate(apiKey.trim(), CallerKind.CLIENT);
        }
        caller.ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.kind().name())))));
        chain.doFilter(request, response);
    }
}
