package com.ohgiraffers.handlermethod.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.util.regex.Pattern;

public final class HttpResultUrlValidator implements ConstraintValidator<HttpResultUrl, String> {
    private static final Pattern ENCODED_CONTROL = Pattern.compile("%(?:0[0-9a-f]|1[0-9a-f]|7f)", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required/length errors belong to @NotBlank/@Size. No DNS lookup or remote fetch.
        if (value == null || value.isBlank()) return true;
        if (value.length() > 1000 || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || ENCODED_CONTROL.matcher(value).find()) return false;
        try {
            URI uri = URI.create(value);
            int port = uri.getPort();
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null && (port == -1 || port > 0 && port <= 65535);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
