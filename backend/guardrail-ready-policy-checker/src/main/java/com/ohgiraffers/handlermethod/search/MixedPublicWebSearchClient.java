package com.ohgiraffers.handlermethod.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.*;

/** Two bounded Naver queries. KBA pages are found through Naver's index, never scraped directly. */
@Component
@Primary
public class MixedPublicWebSearchClient implements ExternalSearchClient {
    private static final Logger log = LoggerFactory.getLogger(MixedPublicWebSearchClient.class);
    private final NaverWebSearchClient naver;
    private final int limit;

    public MixedPublicWebSearchClient(NaverWebSearchClient naver, @Value("${search.max-results:3}") int limit) {
        if (limit < 1 || limit > 5) throw new IllegalArgumentException("Invalid search limit");
        this.naver = naver; this.limit = limit;
    }
    @Override public boolean configured() { return naver.configured(); }
    @Override public List<Result> search(String query) { return searchWithSources(query).results(); }

    @Override public SearchBatch searchWithSources(String query) {
        if (!configured()) throw new SearchFailure("SEARCH_NOT_CONFIGURED");
        if (query == null || query.isBlank() || query.length() > 300) throw new SearchFailure("INVALID_QUERY");
        // Reserve room for the server-owned domain hint, even for a maximum-length generated query.
        String base = query.trim().replaceAll("\\s+", " ");
        if (base.length() > 270) base = base.substring(0, 270).trim();
        if (!base.contains("변호사")) base += " 변호사";
        List<Result> kba = List.of(), general = List.of();
        SearchFailure kbaFailure = null, generalFailure = null;
        try { kba = naver.search(base + " site:klaw.or.kr").stream().filter(item -> SearchText.kbaPublicUrl(item.url())).toList(); }
        catch (SearchFailure failure) { checkInterrupted(failure); kbaFailure = failure; log.warn("KBA-targeted search unavailable: {}", failure.code()); }
        try { general = naver.search(base); }
        catch (SearchFailure failure) { checkInterrupted(failure); generalFailure = failure; log.warn("General web search unavailable: {}", failure.code()); }

        Map<String, Result> unique = new LinkedHashMap<>();
        for (var item : kba) add(unique, item);
        for (var item : general) add(unique, item);
        var official = unique.values().stream().filter(item -> SearchText.kbaPublicUrl(item.url())).iterator();
        var web = unique.values().stream().filter(item -> !SearchText.kbaPublicUrl(item.url())).iterator();
        List<Result> merged = new ArrayList<>();
        // Official first, then alternate so both sources remain visible when available.
        while (merged.size() < limit && (official.hasNext() || web.hasNext())) {
            if (official.hasNext()) merged.add(official.next());
            if (merged.size() < limit && web.hasNext()) merged.add(web.next());
        }
        // A failed leg plus an empty healthy leg is not evidence that there are no results.
        if (merged.isEmpty()) {
            if (generalFailure != null) throw generalFailure;
            if (kbaFailure != null) throw kbaFailure;
        }
        String warning = kbaFailure != null ? "KBA_SEARCH_UNAVAILABLE"
                : generalFailure != null ? "GENERAL_SEARCH_UNAVAILABLE" : null;
        return new SearchBatch(merged, warning);
    }
    private static void add(Map<String, Result> unique, Result item) {
        if (SearchText.publicUrl(item.url())) unique.putIfAbsent(SearchText.resultKey(item.url()), item);
    }
    private static void checkInterrupted(SearchFailure failure) {
        if (Thread.currentThread().isInterrupted() || "SEARCH_INTERRUPTED".equals(failure.code())) throw failure;
    }
}
