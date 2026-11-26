package com.example.quanlydaotao.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.quanlydaotao.service.JwtTokenService;
import com.example.quanlydaotao.service.JwtTokenService.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiAuthenticationInterceptor implements HandlerInterceptor {

    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/refresh",
            "/api/auth/logout");

    private final JwtTokenService tokens;

    public ApiAuthenticationInterceptor(JwtTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getServletPath();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_ENDPOINTS.contains(path)) {
            return true;
        }

        AuthenticatedUser user = tokens.findByAuthorization(request.getHeader("Authorization"));
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Vui lòng đăng nhập bằng access token hợp lệ.\"}");
            return false;
        }
        if (!path.startsWith("/api/auth/") && !"ADMIN".equalsIgnoreCase(user.role())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Bạn không có quyền thực hiện thao tác này.\"}");
            return false;
        }

        request.setAttribute("authenticatedUser", user);
        return true;
    }
}
