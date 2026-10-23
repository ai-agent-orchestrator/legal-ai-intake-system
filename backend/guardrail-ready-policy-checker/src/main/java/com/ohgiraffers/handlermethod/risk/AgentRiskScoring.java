package com.ohgiraffers.handlermethod.risk;

import java.util.List;

/** Request-local scoring. Aggregate Prometheus counters are never scoring inputs. */
public final class AgentRiskScoring {
    public static final int BLOCK_THRESHOLD = 80;
    public static final int POLICY = 40, EXTERNAL = 25, TOOL_ERROR = 15, APPROVAL = 20, DB_WRITE = 15;
    private AgentRiskScoring() { }

    public static int retryPoints(int retries) {
        return (int) Math.min(Math.max(0L, retries) * 8L, 25L);
    }

    public static int behaviorPoints(List<String> signals) {
        return (signals.contains("POLICY_VIOLATION") ? POLICY : 0)
                + (signals.contains("EXTERNAL_API_CALL") ? EXTERNAL : 0)
                + (signals.contains("TOOL_ERROR") ? TOOL_ERROR : 0)
                + (signals.contains("APPROVAL_REQUIRED") ? APPROVAL : 0)
                + (signals.contains("DB_WRITE") ? DB_WRITE : 0);
    }

    public static Breakdown breakdown(List<String> signals, Integer retries, int storedTotal) {
        int behavior = behaviorPoints(signals);
        if (retries != null) {
            int retry = retryPoints(retries);
            return Math.min(behavior + retry, 100) == storedTotal
                    ? new Breakdown(behavior, retry, storedTotal, BLOCK_THRESHOLD) : null;
        }
        // Older histories may lack an unambiguous matching run. Never invent retry usage.
        var possibilities = signals.contains("RETRY") ? List.of(8, 16, 24, 25) : List.of(0);
        var matches = possibilities.stream().filter(points -> Math.min(behavior + points, 100) == storedTotal).toList();
        return matches.size() == 1 ? new Breakdown(behavior, matches.getFirst(), storedTotal, BLOCK_THRESHOLD) : null;
    }

    public record Breakdown(int behaviorPoints, int retryPoints, int total, int threshold) { }
}
