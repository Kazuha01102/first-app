package myfirstapp.monitor;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MonitoringFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(MonitoringFilter.class);
    private final HttpMetrics metrics;
    public MonitoringFilter(HttpMetrics metrics) { this.metrics = metrics; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        if (request.getRequestURI().startsWith("/api/monitor")) response.setHeader("Cache-Control", "no-store");
        long start = System.nanoTime();
        boolean failed = true;
        try { chain.doFilter(request, response); failed = false; }
        finally {
            int status = failed ? 500 : response.getStatus();
            metrics.record(status, System.nanoTime() - start, request.getRequestURI().startsWith("/api/monitor"));
            // No request path/query, headers, input values or exception text in the log view.
            if (status >= 500) log.error("HTTP request completed with server error {}", status);
            else if (status >= 400) log.warn("HTTP request completed with client error {}", status);
        }
    }
}
