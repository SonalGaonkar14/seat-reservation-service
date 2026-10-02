package com.example.seatreservation.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthFilter implements Filter {
    @Value("${app.admin-token}")
    private String adminToken;
    @Value("${app.user-token-prefix:user-}")
    private String userPrefix;
    public static final String USER = "authenticatedUser";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        String h = r.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (token.equals(adminToken)) r.setAttribute(USER, "admin");
            else if (token.startsWith(userPrefix) && token.length() > userPrefix.length())
                r.setAttribute(USER, token.substring(userPrefix.length()));
        }
        chain.doFilter(req, res);
    }
}
