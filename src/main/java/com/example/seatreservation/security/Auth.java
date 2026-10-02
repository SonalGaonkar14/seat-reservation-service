package com.example.seatreservation.security;

import com.example.seatreservation.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;

public final class Auth {
    private Auth() {
    }

    public static String user(HttpServletRequest r) {
        Object u = r.getAttribute(AuthFilter.USER);
        if (u == null) throw new DomainException(401, "unauthorized", "Bearer token required");
        return (String) u;
    }

    public static void admin(HttpServletRequest r) {
        String u = user(r);
        if (!"admin".equals(u)) throw new DomainException(403, "forbidden", "Admin token required");
    }
}
