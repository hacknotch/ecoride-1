package com.ecoride.config;

import com.ecoride.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Deque<Long>> requestBuckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = isAuthPath(path) ? properties.getAuthRequestsPerMinute() : properties.getRequestsPerMinute();

        String key = buildKey(request, isAuthPath(path));

        if (!allowRequest(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Too many requests. Please try again in a minute.")));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean allowRequest(String key, int limit) {
        Deque<Long> queue = requestBuckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = Instant.now().toEpochMilli();
        long earliestAllowed = now - WINDOW_MS;

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < earliestAllowed) {
                queue.pollFirst();
            }

            if (queue.size() >= limit) {
                return false;
            }

            queue.addLast(now);
            return true;
        }
    }

    private boolean isAuthPath(String path) {
        return path.endsWith("/auth/login") || path.endsWith("/auth/register");
    }

    private String buildKey(HttpServletRequest request, boolean authPath) {
        String ip = extractClientIp(request);
        return (authPath ? "AUTH:" : "API:") + ip;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
