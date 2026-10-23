package com.ohgiraffers.handlermethod.search;

import java.util.List;

public interface ExternalSearchClient {
    boolean configured();
    List<Result> search(String query);
    default SearchBatch searchWithSources(String query) { return new SearchBatch(search(query), null); }
    record SearchBatch(List<Result> results, String sourceWarningCode) {
        public SearchBatch { results = List.copyOf(results); }
    }
    record Result(String title, String url, String snippet) {
        public String sourceType() { return SearchText.kbaPublicUrl(url) ? "KBA_PUBLIC_WEB" : "NAVER_WEB"; }
    }
}
