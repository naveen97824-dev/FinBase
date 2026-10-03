package com.finbase.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves {@code financier_id} from the request's JWT and stores it on
 * {@link CurrentFinancierHolder} before the controller runs. Controllers
 * read it from there and pass it explicitly to
 * {@link com.finbase.db.TenantSession#execute}; this interceptor never
 * touches the database itself.
 */
@Component
@RequiredArgsConstructor
public class FinancierContextInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CurrentFinancierHolder currentFinancierHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            currentFinancierHolder.setFinancierId(jwtService.verifyAndExtractFinancierId(token));
        } catch (JwtException e) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        return true;
    }
}
