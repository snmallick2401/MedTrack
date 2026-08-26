package com.medtrack.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService j) {
        this.jwt = j;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest r,
            HttpServletResponse s,
            FilterChain c) throws ServletException, IOException {
        String h = r.getHeader(HttpHeaders.AUTHORIZATION);
        if (h != null && h.startsWith("Bearer ")) {
            try {
                var claims = jwt.claims(h.substring(7));
                List<SimpleGrantedAuthority> a = new ArrayList<>();
                a.add(new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class)));
                List<?> permissions = claims.get("permissions", List.class);
                if (permissions != null) {
                    for (Object p : permissions) {
                        a.add(new SimpleGrantedAuthority(p.toString()));
                    }
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, a));
            } catch (JwtException ignored) {
            }
        }
        c.doFilter(r, s);
    }
}
