package com.ohgiraffers.handlermethod.support;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 재전송 판별에 쓰는 요청 지문(SHA-256)과 Idempotency-Key 형식 검사.
 * 해시만 저장·비교하고 원문은 저장하거나 로그에 남기지 않는다.
 */
public final class RequestFingerprint {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    private RequestFingerprint() {
    }

    /**
     * 값들을 구분자 0x1F로 이어 해시한다. 화면07 메모 지문(memo-v1)과 2026-10-23 이전에 저장된 문의 지문(intake-v1)이 이 방식이다.
     * 자유 입력 값이 둘 이상이면 경계가 모호해질 수 있으므로 새 지문에는 {@link #ofRecord}를 쓴다.
     */
    public static String sha256(Object... parts) {
        MessageDigest digest = newDigest();
        for (Object part : parts) {
            digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0x1F);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * record의 모든 컴포넌트(이름과 값)를 길이 접두 방식으로 이어 해시한다.
     * 필드가 추가되면 자동으로 지문에 포함되고, 필드 사이 경계·null과 빈 문자열·리스트 원소 경계가 섞이지 않는다.
     * (record toString은 "a, b=c" 같은 값이 다음 필드처럼 읽혀 서로 다른 요청이 같은 문자열이 될 수 있다.)
     */
    public static String ofRecord(String version, Record value) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, version);
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            append(canonical, component.getName());
            try {
                append(canonical, component.getAccessor().invoke(value));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read request field " + component.getName(), exception);
            }
        }
        return HexFormat.of().formatHex(newDigest().digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("N;");
        } else if (value instanceof Collection<?> values) {
            out.append('L').append(values.size()).append('[');
            values.forEach(item -> append(out, item));
            out.append(']');
        } else {
            String text = String.valueOf(value);
            out.append('S').append(text.length()).append(':').append(text).append(';');
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** null/blank이면 null, 형식이 틀리면 IllegalArgumentException(메시지에 키 값은 넣지 않는다). */
    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (!KEY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must be 8-100 characters of [A-Za-z0-9._:-]");
        }
        return trimmed;
    }
}
