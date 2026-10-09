package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AgentJobStartResponse;
import com.ohgiraffers.handlermethod.dto.AgentJobStatusResponse;
import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeRequest;
import com.ohgiraffers.handlermethod.dto.AgentRiskAnalyzeResponse;
import com.ohgiraffers.handlermethod.entity.AgentJobHistory;
import com.ohgiraffers.handlermethod.repository.AgentJobHistoryRepository;
import com.ohgiraffers.handlermethod.support.AgentMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class AgentJobService {

    private final AgentJobHistoryRepository agentJobHistoryRepository;
    private final AgentRiskDecisionService agentRiskDecisionService;
    private final AgentMetricRecorder agentMetricRecorder;
    private final ExecutorService agentJobExecutor;

    public AgentJobService(AgentJobHistoryRepository agentJobHistoryRepository,
                           AgentRiskDecisionService agentRiskDecisionService,
                           AgentMetricRecorder agentMetricRecorder,
                           ExecutorService agentJobExecutor) {
        this.agentJobHistoryRepository = agentJobHistoryRepository;
        this.agentRiskDecisionService = agentRiskDecisionService;
        this.agentMetricRecorder = agentMetricRecorder;
        this.agentJobExecutor = agentJobExecutor;
    }

    public AgentJobStartResponse start(AgentRiskAnalyzeRequest request) {
        String userInput = valueOrDefault(request == null ? null : request.userInput(), "empty agent request");
        String toolName = valueOrDefault(request == null ? null : request.toolName(), "unknown");
        int retryCount = positiveOrDefault(request == null ? null : request.retryCount(), 0);
        String traceId = TraceContext.currentTraceId();

        AgentJobHistory job = agentJobHistoryRepository.save(
                AgentJobHistory.createPending(userInput, toolName, retryCount, traceId)
        );
        agentMetricRecorder.recordJobStarted();

        agentJobExecutor.submit(() -> process(job.getId(), request));

        return new AgentJobStartResponse(
                job.getId(),
                job.getStatus().name(),
                "Agent risk job accepted and scheduled on a Java 21 virtual thread.",
                traceId
        );
    }

    public AgentJobStatusResponse findById(Long jobId) {
        AgentJobHistory history = agentJobHistoryRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Agent job not found: " + jobId));
        return AgentJobStatusResponse.from(history);
    }

    public List<AgentJobStatusResponse> findRecent() {
        return agentJobHistoryRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(AgentJobStatusResponse::from)
                .toList();
    }

    private void process(Long jobId, AgentRiskAnalyzeRequest request) {
        try {
            AgentJobHistory runningJob = agentJobHistoryRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Agent job not found: " + jobId));
            runningJob.markRunning();
            agentJobHistoryRepository.save(runningJob);

            simulateWaitingWork();

            AgentRiskAnalyzeResponse result = agentRiskDecisionService.analyze(request);

            AgentJobHistory completedJob = agentJobHistoryRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Agent job not found: " + jobId));
            completedJob.markCompleted(
                    result.decision(),
                    result.riskScore(),
                    result.riskLevel(),
                    result.recommendedAction()
            );
            agentJobHistoryRepository.save(completedJob);
            agentMetricRecorder.recordJobCompleted(result.decision(), result.riskLevel());
        } catch (Exception exception) {
            AgentJobHistory failedJob = agentJobHistoryRepository.findById(jobId).orElse(null);
            if (failedJob != null) {
                failedJob.markFailed(exception.getMessage());
                agentJobHistoryRepository.save(failedJob);
            }
            agentMetricRecorder.recordJobFailed(exception.getClass().getSimpleName());
        }
    }

    private void simulateWaitingWork() throws InterruptedException {
        Thread.sleep(300);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        if (value == null || value < 0) {
            return defaultValue;
        }

        return value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
