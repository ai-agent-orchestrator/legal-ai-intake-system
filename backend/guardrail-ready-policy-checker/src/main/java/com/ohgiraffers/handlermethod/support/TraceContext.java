package com.ohgiraffers.handlermethod.support;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Integer> SQL_STATEMENT_COUNT = ThreadLocal.withInitial(() -> 0);

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
        SQL_STATEMENT_COUNT.set(0);
    }

    public static String currentTraceId() {
        String traceId = TRACE_ID.get();

        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }

        return traceId;
    }

    public static void incrementSqlStatementCount() {
        SQL_STATEMENT_COUNT.set(SQL_STATEMENT_COUNT.get() + 1);
    }

    public static int currentSqlStatementCount() {
        return SQL_STATEMENT_COUNT.get();
    }

    public static void clear() {
        TRACE_ID.remove();
        SQL_STATEMENT_COUNT.remove();
    }
}
