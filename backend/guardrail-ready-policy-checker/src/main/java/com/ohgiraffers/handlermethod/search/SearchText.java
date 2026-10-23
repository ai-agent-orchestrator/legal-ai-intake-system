package com.ohgiraffers.handlermethod.search;

import org.springframework.web.util.HtmlUtils;
import java.net.URI;

final class SearchText {
    private SearchText() { }
    static String plain(String value, int limit) {
        if (value == null) return "";
        String text = HtmlUtils.htmlUnescape(value)
                .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]*>", " ").replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }
    static boolean publicUrl(String value) {
        if (value == null || value.length() > 1000 || value.matches(".*[\\s\\p{Cntrl}].*")) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            // Result URLs are displayed only. Never resolve DNS or fetch these addresses on the server.
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getRawUserInfo() == null && host != null && host.contains(".")
                    && !host.matches("[0-9.]+") && !host.contains(":")
                    && !host.endsWith(".localhost") && !host.endsWith(".local");
        } catch (IllegalArgumentException exception) { return false; }
    }

    static boolean kbaPublicUrl(String value) {
        if (!publicUrl(value)) return false;
        String host = URI.create(value).getHost().toLowerCase(java.util.Locale.ROOT);
        return host.equals("klaw.or.kr") || host.endsWith(".klaw.or.kr")
                || host.equals("koreanbar.or.kr") || host.endsWith(".koreanbar.or.kr");
    }

    /** Keep meaningful query parameters (profile IDs), but ignore fragments and common tracking tags. */
    static String resultKey(String value) {
        URI uri = URI.create(value).normalize();
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT).replaceFirst("^www\\.", "");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) host += ":" + port;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceFirst("/+$", "");
        String query = uri.getRawQuery() == null ? "" : java.util.Arrays.stream(uri.getRawQuery().split("&"))
                .filter(part -> !part.matches("(?i)(utm_[^=]*|fbclid|gclid)=.*"))
                .sorted().collect(java.util.stream.Collectors.joining("&"));
        return host + path + (query.isEmpty() ? "" : "?" + query);
    }
}
