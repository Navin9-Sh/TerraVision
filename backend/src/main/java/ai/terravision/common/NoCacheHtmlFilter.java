package ai.terravision.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Marks every static HTML page no-store so the browser never serves a protected page
 * from its HTTP cache or back/forward cache after logout. The 10 MB Pune map document
 * is a sub-resource of pune-map.html (an iframe, not a page anyone navigates back to)
 * and holds no user data, so it stays cacheable.
 */
@Component
public class NoCacheHtmlFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isHtmlPage = path.equals("/") || path.endsWith(".html");
        return !isHtmlPage || path.endsWith("/pune-lulc-map.html");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        chain.doFilter(request, response);
    }
}
