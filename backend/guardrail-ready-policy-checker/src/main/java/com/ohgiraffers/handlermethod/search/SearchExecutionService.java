package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.cost.CostLimitException;
import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.service.IntakeAccessService;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.concurrent.*;

@Service
public class SearchExecutionService {
    private final SearchExecutionStore store;
    private final IntakeAccessService access;
    private final LegalIntakeRecordService records;
    private final ExternalSearchClient external;
    private final SearchLlmService llm;
    private final boolean configured;
    private final long deadlineMs;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> { Thread thread = new Thread(runnable, "p03-search"); thread.setDaemon(true); return thread; });
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "p03-deadline"); thread.setDaemon(true); return thread;
    });
    private final ConcurrentMap<String, FutureTask<Void>> running = new ConcurrentHashMap<>();

    public SearchExecutionService(SearchExecutionStore store, IntakeAccessService access,
            LegalIntakeRecordService records, ExternalSearchClient external, SearchLlmService llm,
            @Value("${search.enabled:true}") boolean enabled, @Value("${llm.api-key:}") String key,
            @Value("${search.execution-timeout-ms:180000}") long deadlineMs) {
        if (deadlineMs < 100 || deadlineMs > 600000) throw new IllegalArgumentException("Invalid search deadline");
        this.store = store; this.access = access; this.records = records; this.external = external; this.llm = llm;
        configured = enabled && !key.isBlank(); this.deadlineMs = deadlineMs;
    }

    public AiSearchRequestResponse execute(Long id, boolean retryExplanation, Authentication authentication) {
        var request = store.find(id);
        access.requireAccess(request.getIntakeId(), authentication);
        // Idempotent observation of an existing execution is allowed even if provider settings changed.
        boolean eligible = "PREVIEW_READY".equals(request.getStatus()) || (retryExplanation && "PARTIAL".equals(request.getStatus()));
        if (!eligible) return records.findSearchRequest(id, authentication);
        boolean explanationOnly = retryExplanation && "PARTIAL".equals(request.getStatus());
        if (!configured || (!explanationOnly && !external.configured()))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_NOT_CONFIGURED");
        var job = store.claim(id, retryExplanation);
        if (job == null) return records.findSearchRequest(id, authentication);
        String session = "search:" + authentication.getName();
        FutureTask<Void> task = new FutureTask<>(() -> { run(job, session); return null; });
        running.put(job.lease(), task);
        ScheduledFuture<?> deadline = deadlines.schedule(() -> {
            try { store.fail(job, "SEARCH_TIMEOUT"); }
            finally { task.cancel(true); running.remove(job.lease()); }
        }, deadlineMs, TimeUnit.MILLISECONDS);
        try { workers.execute(() -> { try { task.run(); } finally { deadline.cancel(false); running.remove(job.lease()); } }); }
        catch (RejectedExecutionException exception) {
            deadline.cancel(false); running.remove(job.lease()); store.reject(job);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "SEARCH_QUEUE_FULL");
        }
        return records.findSearchRequest(id, authentication);
    }

    private void run(SearchExecutionStore.Job job, String session) {
        try {
            if (!job.explanationOnly()) {
                if (!store.stage(job, "QUERY_GENERATION")) return;
                String query = llm.query(job.condition(), session, () -> store.active(job));
                if (!store.query(job, query) || !store.stage(job, "EXTERNAL_SEARCH")) return;
                var batch = external.searchWithSources(query);
                var collected = batch.results();
                if (!store.sourceWarning(job, batch.sourceWarningCode())) return;
                if (!store.stage(job, "RESULT_PERSISTENCE") || !store.saveResults(job, collected)) return;
                if (collected.isEmpty()) { store.finish(job, "NO_RESULTS", null); return; }
            }
            if (!store.stage(job, "EXPLANATION")) return;
            for (var result : store.unexplained(job)) {
                String text = llm.explain(job.condition(), result, session, () -> store.active(job));
                if (!store.explain(job, result.getId(), text)) return;
            }
            store.finish(job, "COMPLETED", null);
        } catch (CostLimitException exception) { store.fail(job, "SEARCH_COST_BLOCKED");
        } catch (SearchFailure exception) { store.fail(job, exception.code());
        } catch (RuntimeException exception) { store.fail(job, "SEARCH_EXECUTION_ERROR"); }
    }

    // Single application instance: persisted running jobs are not automatically replayed after restart.
    @EventListener(ApplicationReadyEvent.class) public void recoverInterrupted() { store.recoverInterrupted(); }
    @PreDestroy public void shutdown() {
        running.values().forEach(task -> task.cancel(true)); workers.shutdownNow(); deadlines.shutdownNow();
    }
}
