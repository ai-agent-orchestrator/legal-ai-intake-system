package com.ohgiraffers.handlermethod.riskengine;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrivacyOutputGuardService {

    private static final Pattern RESIDENT_REGISTRATION_NUMBER =
            Pattern.compile("\\b(\\d{6})-\\d{7}\\b");
    private static final Pattern PHONE_NUMBER =
            Pattern.compile("\\b(01[016789])-?(\\d)\\d{2,3}-?\\d{4}\\b");
    private static final Pattern CARD_NUMBER =
            Pattern.compile("\\b(\\d{4})-\\d{4}-\\d{4}-\\d{4}\\b");
    private static final Pattern EMAIL =
            Pattern.compile("\\b([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");
    private static final Pattern ACCOUNT_NUMBER =
            Pattern.compile("\\b(\\d{2,6})-(\\d{2,6})-(\\d{3,12})\\b");

    public PrivacyOutputGuardResult mask(String content) {
        if (content == null || content.isBlank()) {
            return new PrivacyOutputGuardResult(content, false, List.of());
        }

        Set<String> piiTypes = new LinkedHashSet<>();
        String masked = content;

        masked = replace(masked, RESIDENT_REGISTRATION_NUMBER, "$1-*******", "RRN", piiTypes);
        masked = replace(masked, PHONE_NUMBER, "$1-$2***-****", "PHONE", piiTypes);
        masked = replace(masked, CARD_NUMBER, "$1-****-****-****", "CARD", piiTypes);
        masked = replaceEmail(masked, piiTypes);
        masked = replaceAccountNumber(masked, piiTypes);

        return new PrivacyOutputGuardResult(masked, !piiTypes.isEmpty(), new ArrayList<>(piiTypes));
    }

    public String maskDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed;
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 1);
    }

    private String replace(String input, Pattern pattern, String replacement, String piiType, Set<String> piiTypes) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            piiTypes.add(piiType);
            return matcher.replaceAll(replacement);
        }
        return input;
    }

    private String replaceEmail(String input, Set<String> piiTypes) {
        Matcher matcher = EMAIL.matcher(input);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "***" + matcher.group(3)));
        }
        matcher.appendTail(buffer);
        if (found) {
            piiTypes.add("EMAIL");
        }
        return buffer.toString();
    }

    private String replaceAccountNumber(String input, Set<String> piiTypes) {
        Matcher matcher = ACCOUNT_NUMBER.matcher(input);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            String fullMatch = matcher.group();
            if (PHONE_NUMBER.matcher(fullMatch).matches()
                    || RESIDENT_REGISTRATION_NUMBER.matcher(fullMatch).matches()
                    || CARD_NUMBER.matcher(fullMatch).matches()) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(fullMatch));
                continue;
            }
            found = true;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "-***-******"));
        }
        matcher.appendTail(buffer);
        if (found) {
            piiTypes.add("ACCOUNT");
        }
        return buffer.toString();
    }
}
