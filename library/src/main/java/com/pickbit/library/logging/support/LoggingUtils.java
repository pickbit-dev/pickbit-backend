package com.pickbit.library.logging.support;

import org.slf4j.MDC;
import jakarta.servlet.http.HttpServletRequest;

public class LoggingUtils {

    private static final String MDC_METHOD = "method";
    private static final String MDC_REQUEST_URL = "request_url";
    private static final String MDC_QUERY = "query";

    public static void setBasicMDC(HttpServletRequest request) {

        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_REQUEST_URL, request.getRequestURI());

        String query = request.getQueryString();
        if (query != null) {
            MDC.put(MDC_QUERY, query.length() > 200 ? query.substring(0,200) : query);
        }
    }

    public static void clearBasicMDC() {
        MDC.remove(MDC_METHOD);
        MDC.remove(MDC_REQUEST_URL);
        MDC.remove(MDC_QUERY);
    }

    public static String getFullUrl(String uri, String queryString) {
        return queryString != null ? uri + "?" + queryString : uri;
    }

    public static String getPerformanceCategory(long duration) {
        if (duration < 100) return "fast";
        if (duration < 500) return "normal";
        if (duration < 1000) return "slow";
        return "very_slow";
    }

    public static boolean isLoggableContentType(String contentType) {

        if (contentType == null) return false;

        String type = contentType.toLowerCase();

        return type.contains("application/json") ||
                type.contains("application/xml") ||
                type.contains("text/") ||
                type.contains("application/x-www-form-urlencoded");
    }

    public static boolean isMultipartContentType(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("multipart/form-data");
    }

    public static boolean shouldSkipLogging(String uri, String[] excludedPaths) {

        if (excludedPaths == null) {
            return false;
        }

        for (String excludedPath : excludedPaths) {
            if (uri.startsWith(excludedPath)) {
                return true;
            }
        }

        return false;
    }
}
