package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.entity.*;
import com.ohgiraffers.handlermethod.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short, locked transactions only. No network calls while holding a database lock. */
@Service
public class SearchExecutionStore {
    private final AiSearchRequestRepository requests;
    private final LawyerSearchConditionRepository conditions;
    private final SearchResultSnapshotRepository results;
    public SearchExecutionStore(AiSearchRequestRepository requests, LawyerSearchConditionRepository conditions,
                                SearchResultSnapshotRepository results) {
        this.requests = requests; this.conditions = conditions; this.results = results;
    }
    @Transactional(readOnly = true)
    public AiSearchRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search request not found"));
    }
    @Transactional
    public Job claim(Long id, boolean retryExplanation) {
        AiSearchRequest request = locked(id);
        boolean retry = retryExplanation && "PARTIAL".equals(request.getStatus());
        if (!"PREVIEW_READY".equals(request.getStatus()) && !retry) return null;
        if (!"KBA_NAVER_PUBLIC_WEB".equals(request.getSourcePolicy()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported source policy");
        LawyerSearchCondition condition = conditions.findById(request.getConditionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search condition not found"));
        if (!Objects.equals(condition.getIntakeId(), request.getIntakeId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Search condition does not belong to intake");
        request.beginExecution(UUID.randomUUID().toString(), retry);
        return new Job(id, request.getExecutionId(), request.getExecutionLease(), condition, retry);
    }
    @Transactional(readOnly = true)
    public boolean active(Job job) {
        return requests.findById(job.id()).map(request -> matches(request, job)).orElse(false);
    }
    @Transactional
    public boolean stage(Job job, String stage) {
        var request = locked(job.id()); if (!matches(request, job)) return false;
        request.advanceExecution(stage); return true;
    }
    @Transactional
    public boolean query(Job job, String query) {
        var request = locked(job.id()); if (!matches(request, job)) return false;
        request.storeQuery(query); return true;
    }
    @Transactional
    public boolean saveResults(Job job, List<ExternalSearchClient.Result> collected) {
        var request = locked(job.id()); if (!matches(request, job)) return false;
        var existing = results.findBySearchRequestIdOrderByDisplayOrder(job.id());
        int order = existing.stream().mapToInt(SearchResultSnapshot::getDisplayOrder).max().orElse(0);
        var urls = existing.stream().filter(result -> job.batch().equals(result.getExecutionId()))
                .map(SearchResultSnapshot::getUrl).collect(java.util.stream.Collectors.toSet());
        for (var item : collected) {
            if (urls.add(item.url())) results.save(SearchResultSnapshot.collected(job.id(), item.sourceType(), item.title(), item.url(), item.snippet(), ++order, job.batch()));
        }
        return true;
    }
    @Transactional
    public boolean sourceWarning(Job job, String code) {
        var request = locked(job.id()); if (!matches(request, job)) return false;
        request.recordSourceWarning(code); return true;
    }
    @Transactional(readOnly = true)
    public List<SearchResultSnapshot> unexplained(Job job) {
        return results.findBySearchRequestIdOrderByDisplayOrder(job.id()).stream()
                .filter(result -> job.batch().equals(result.getExecutionId()) && result.getExplanation() == null).toList();
    }
    @Transactional
    public boolean explain(Job job, Long resultId, String explanation) {
        var request = locked(job.id()); if (!matches(request, job)) return false;
        var result = results.findById(resultId).orElseThrow();
        if (!Objects.equals(result.getSearchRequestId(), job.id()) || !job.batch().equals(result.getExecutionId())) return false;
        result.explain(explanation); return true;
    }
    @Transactional
    public void finish(Job job, String status, String code) {
        var request = locked(job.id()); if (!matches(request, job)) return;
        request.finishExecution(status, code);
    }
    @Transactional
    public void fail(Job job, String code) {
        var request = locked(job.id()); if (!matches(request, job)) return;
        boolean saved = results.findBySearchRequestIdOrderByDisplayOrder(job.id()).stream()
                .anyMatch(result -> job.batch().equals(result.getExecutionId()));
        request.finishExecution(saved ? "PARTIAL" : "FAILED", code);
    }
    @Transactional
    public void reject(Job job) {
        var request = locked(job.id()); if (!matches(request, job)) return;
        if (job.explanationOnly()) request.finishExecution("PARTIAL", "SEARCH_QUEUE_FULL");
        else request.rejectQueuedExecution();
    }
    @Transactional
    public void recoverInterrupted() {
        for (var request : requests.findByStatusIn(List.of("QUEUED", "RUNNING"))) {
            var job = new Job(request.getId(), request.getExecutionId(), request.getExecutionLease(), null, false);
            fail(job, "SEARCH_INTERRUPTED");
        }
    }
    private AiSearchRequest locked(Long id) {
        return requests.findForExecution(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search request not found"));
    }
    private boolean matches(AiSearchRequest request, Job job) { return request.isExecuting() && Objects.equals(request.getExecutionLease(), job.lease()); }
    public record Job(Long id, String batch, String lease, LawyerSearchCondition condition, boolean explanationOnly) { }
}
