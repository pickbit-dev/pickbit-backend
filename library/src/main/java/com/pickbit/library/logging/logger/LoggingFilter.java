package com.pickbit.library.logging.logger;

import com.pickbit.library.logging.config.FilterProperties;
import com.pickbit.library.logging.support.LoggingUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class LoggingFilter extends OncePerRequestFilter {

    private final RequestLogger requestLogger;
    private final ResponseLogger responseLogger;
    private final FilterProperties filterProperties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if (LoggingUtils.shouldSkipLogging(request.getRequestURI(), filterProperties.getExcludedPaths())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (LoggingUtils.isMultipartContentType(request.getContentType())) {
            doFilterWithoutRequestBodyCaching(request, response, filterChain);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            LoggingUtils.setBasicMDC(requestWrapper);

            requestLogger.logRequestStart(requestWrapper);

            filterChain.doFilter(requestWrapper, responseWrapper);

            long duration = System.currentTimeMillis() - startTime;
            requestLogger.logRequestComplete(requestWrapper, filterProperties);
            responseLogger.logResponse(requestWrapper, responseWrapper, duration, filterProperties);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error(
                    "Request processing failed: {} {} [{}ms]",
                    requestWrapper.getMethod(),
                    requestWrapper.getRequestURI(),
                    duration,
                    e
            );
            throw e;
        } finally {
            responseWrapper.copyBodyToResponse();
            LoggingUtils.clearBasicMDC();
        }
    }

    private void doFilterWithoutRequestBodyCaching(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long startTime = System.currentTimeMillis();

        try {
            LoggingUtils.setBasicMDC(request);
            requestLogger.logRequestStart(request);

            filterChain.doFilter(request, responseWrapper);

            long duration = System.currentTimeMillis() - startTime;
            responseLogger.logResponse(request, responseWrapper, duration, filterProperties);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error(
                    "Request processing failed: {} {} [{}ms]",
                    request.getMethod(),
                    request.getRequestURI(),
                    duration,
                    e
            );
            throw e;
        } finally {
            responseWrapper.copyBodyToResponse();
            LoggingUtils.clearBasicMDC();
        }
    }
}
