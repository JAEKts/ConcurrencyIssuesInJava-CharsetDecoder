package com.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Environment env;

    // Toggle response stack traces via config (default: off)
    @Value("${app.errors.includeStacktrace:false}")
    private boolean includeStacktraceByDefault;

    // Limit frames so responses don't explode
    @Value("${app.errors.stacktrace.maxFrames:50}")
    private int maxFrames;

    public ApiExceptionHandler(Environment env) {
        this.env = env;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest req) {

        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, ex, req, ex.getMessage());
        maybeAttachStacktrace(body, ex, req);

        // Log 4xx at WARN
        log.warn("400 at {} {} traceId={} -> {}: {}",
                req.getMethod(),
                req.getRequestURI(),
                MDC.get("traceId"),
                ex.getClass().getSimpleName(),
                Optional.ofNullable(ex.getMessage()).orElse(""));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex, HttpServletRequest req) {

        Map<String, Object> body = baseBody(HttpStatus.INTERNAL_SERVER_ERROR, ex, req, "Unexpected error.");
        maybeAttachStacktrace(body, ex, req);

        // Log 5xx at ERROR with the throwable so logs contain the official stack
        log.error("500 at {} {} traceId={} -> {}",
                req.getMethod(),
                req.getRequestURI(),
                MDC.get("traceId"),
                ex.toString(),
                ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /* ----------------- helpers ----------------- */

    private Map<String, Object> baseBody(HttpStatus status, Throwable ex, HttpServletRequest req, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", req.getRequestURI());
        body.put("exception", ex.getClass().getName());

        // If you have tracing (e.g., Micrometer/Sleuth), propagate an id
        String traceId = MDC.get("traceId");
        if (traceId != null) body.put("traceId", traceId);

        return body;
    }

    private void maybeAttachStacktrace(Map<String, Object> body, Throwable ex, HttpServletRequest req) {
        if (!shouldIncludeStacktrace()) return;

        // Full, official Java stack as a single string (like Boot's default "trace")
        body.put("trace", fullStack(ex));

        // Also include top-N frames as an array (handy for machines / grep)
        List<String> frames = Stream.of(ex.getStackTrace())
                .limit(maxFrames)
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());
        body.put("stackTrace", frames);

        // Suppressed exceptions (useful with try-with-resources)
        Throwable[] suppressed = ex.getSuppressed();
        if (suppressed != null && suppressed.length > 0) {
            List<String> sup = Arrays.stream(suppressed)
                    .map(t -> t.getClass().getName() + ": " + Optional.ofNullable(t.getMessage()).orElse(""))
                    .collect(Collectors.toList());
            body.put("suppressed", sup);
        }

        // Root cause details
        Throwable root = rootCause(ex);
        if (root != null && root != ex) {
            body.put("rootCause", root.getClass().getName());
            body.put("rootMessage", Optional.ofNullable(root.getMessage()).orElse(""));
        }
    }

    // Removed the query-param gate. Only config/profile controls remain.
    private boolean shouldIncludeStacktrace() {
        if (includeStacktraceByDefault) return true; // explicit opt-in via config

        for (String p : env.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p)) return true;
        }
        return false; // never via request anymore
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t, next;
        while (cur != null && (next = cur.getCause()) != null && next != cur) {
            cur = next;
        }
        return cur;
    }

    private String fullStack(Throwable ex) {
        StringWriter sw = new StringWriter(4096);
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw); // includes causes + suppressed
        return sw.toString();
    }
}
