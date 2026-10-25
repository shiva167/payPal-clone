
        package com.paypal.api_gateway.filters;

import com.paypal.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/signup",
            "/auth/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        String normalizedPath = path.replaceAll("/+$", "");

        System.out.println("=================================");
        System.out.println("JWT FILTER CALLED");
        System.out.println("REQUEST PATH = " + normalizedPath);

        // Public endpoints
        if (PUBLIC_PATHS.contains(normalizedPath)) {

            System.out.println("PUBLIC PATH - JWT CHECK SKIPPED");

            return chain.filter(exchange)
                    .doOnSuccess(v ->
                            System.out.println("PUBLIC REQUEST SUCCESS"))
                    .doOnError(e ->
                            System.err.println("PUBLIC REQUEST ERROR: " + e.getMessage()));
        }

        // Get Authorization header
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        System.out.println("AUTH HEADER = " + authHeader);

        // Authorization header missing/invalid
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            System.out.println("MISSING OR INVALID BEARER TOKEN");

            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }

        try {

            String token = authHeader.substring(7);

            System.out.println("TOKEN RECEIVED");

            Claims claims = JwtUtil.validateToken(token);

            System.out.println("TOKEN VALID");
            System.out.println("USER EMAIL = " + claims.getSubject());
//
//            // Create mutated request with user email
//            ServerWebExchange mutatedExchange = exchange.mutate()
//                    .request(exchange.getRequest().mutate()
//                            .header("X-User-Email", claims.getSubject()).header("X-user-Id",claims.get("user-id",String.class)).header("X-User-Role",claims.get("role",String.class))
//
//                            .build())
//                    .build();
// Create mutated request with user email, id, and role
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-User-Email", claims.getSubject())
                            .header("X-User-Id", claims.get("user-id", String.class))      // Fixed casing: X-User-Id
                            .header("X-User-Role", claims.get("role", String.class))
                            .build())
                    .build();
            System.out.println("X-User-Email HEADER ADDED");

            return chain.filter(mutatedExchange)
                    .doOnSuccess(v ->
                            System.out.println("REQUEST SUCCESS"))
                    .doOnError(e ->
                            System.err.println("REQUEST ERROR: " + e.getMessage()));

        } catch (Exception e) {

            System.err.println("JWT VALIDATION FAILED");
            System.err.println("ERROR = " + e.getMessage());

            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
