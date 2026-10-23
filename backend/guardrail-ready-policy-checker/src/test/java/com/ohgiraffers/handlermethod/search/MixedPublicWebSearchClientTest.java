package com.ohgiraffers.handlermethod.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MixedPublicWebSearchClientTest {
    NaverWebSearchClient naver;
    MixedPublicWebSearchClient client;
    static final String QUERY = "서울 민사 대여금 변호사";
    static final String KBA_QUERY = QUERY + " site:klaw.or.kr";
    static ExternalSearchClient.Result item(String url) { return new ExternalSearchClient.Result("공개 자료", url, "서울 민사"); }

    @BeforeEach void setup() {
        naver = mock(NaverWebSearchClient.class);
        when(naver.configured()).thenReturn(true);
        client = new MixedPublicWebSearchClient(naver, 3);
    }
    @Test void mixesOfficialAndGeneralPagesWithoutDuplicateProfilesOrLosingMeaningfulIds() {
        var official = item("https://www.klaw.or.kr/profile?id=1");
        when(naver.search(KBA_QUERY)).thenReturn(List.of(official,
                item("https://klaw.or.kr/profile?id=2"), item("https://klaw.or.kr.evil.example/profile")));
        when(naver.search(QUERY)).thenReturn(List.of(item("https://klaw.or.kr/profile?utm_source=naver&id=1#intro"),
                item("https://law.example.org/seoul"), item("https://law.example.org/seoul/#contact")));
        var batch = client.searchWithSources(QUERY);
        assertThat(batch.sourceWarningCode()).isNull();
        assertThat(batch.results()).extracting(ExternalSearchClient.Result::url)
                .containsExactly(official.url(), "https://law.example.org/seoul", "https://klaw.or.kr/profile?id=2");
        assertThat(batch.results()).extracting(ExternalSearchClient.Result::sourceType)
                .containsExactly("KBA_PUBLIC_WEB", "NAVER_WEB", "KBA_PUBLIC_WEB");
        verify(naver).search(KBA_QUERY); verify(naver).search(QUERY);
    }
    @Test void acceptsOnlyRealOfficialHostsEvenWhenTitleOrPathClaimsToBeKba() {
        for (String url : List.of("https://klaw.or.kr.evil.example/a", "https://evil.example/klaw.or.kr",
                "https://klaw.or.kr@evil.example/a", "https://evilklaw.or.kr/a", "javascript:alert(1)"))
            assertThat(SearchText.kbaPublicUrl(url)).as(url).isFalse();
        assertThat(SearchText.kbaPublicUrl("https://www.koreanbar.or.kr/pages/member/list.asp")).isTrue();
        when(naver.search(KBA_QUERY)).thenReturn(List.of(item("https://evil.example/klaw.or.kr")));
        when(naver.search(QUERY)).thenReturn(List.of(item("https://law.example.org/a")));
        assertThat(client.search(QUERY)).extracting(ExternalSearchClient.Result::url).containsExactly("https://law.example.org/a");
    }
    @Test void missingIndexedKbaPagesUsesGeneralResultsWithoutFabricatingAnOfficialHit() {
        when(naver.search(KBA_QUERY)).thenReturn(List.of());
        when(naver.search(QUERY)).thenReturn(List.of(item("https://law.example.org/a"), item("https://law.example.org/b"), item("https://law.example.org/c")));
        assertThat(client.searchWithSources(QUERY).results()).hasSize(3).allSatisfy(result -> assertThat(result.sourceType()).isEqualTo("NAVER_WEB"));
        assertThat(new MixedPublicWebSearchClient(naver, 1).search(QUERY)).hasSize(1);
    }
    @Test void unavailableKbaSearchPreservesGeneralResultsAndExplicitWarning() {
        when(naver.search(KBA_QUERY)).thenThrow(new SearchFailure("SEARCH_PROVIDER_ERROR"));
        when(naver.search(QUERY)).thenReturn(List.of(item("https://law.example.org/a")));
        var batch = client.searchWithSources(QUERY);
        assertThat(batch.results()).hasSize(1);
        assertThat(batch.sourceWarningCode()).isEqualTo("KBA_SEARCH_UNAVAILABLE");
    }
    @Test void unavailableGeneralSearchPreservesOfficialResultsAndExplicitWarning() {
        when(naver.search(KBA_QUERY)).thenReturn(List.of(item("https://klaw.or.kr/profile?id=1")));
        when(naver.search(QUERY)).thenThrow(new SearchFailure("SEARCH_PROVIDER_ERROR"));
        var batch = client.searchWithSources(QUERY);
        assertThat(batch.results()).hasSize(1);
        assertThat(batch.sourceWarningCode()).isEqualTo("GENERAL_SEARCH_UNAVAILABLE");
    }
    @Test void failuresCannotBecomeFalseNoResultsButTwoSuccessfulEmptySearchesCan() {
        when(naver.search(KBA_QUERY)).thenThrow(new SearchFailure("SEARCH_PROVIDER_ERROR"));
        when(naver.search(QUERY)).thenReturn(List.of());
        assertThatThrownBy(() -> client.search(QUERY)).hasMessage("SEARCH_PROVIDER_ERROR");
        when(naver.search(QUERY)).thenThrow(new SearchFailure("SEARCH_RESPONSE_INVALID"));
        assertThatThrownBy(() -> client.search(QUERY)).hasMessage("SEARCH_RESPONSE_INVALID");
        reset(naver); when(naver.configured()).thenReturn(true); when(naver.search(anyString())).thenReturn(List.of());
        var batch = client.searchWithSources(QUERY);
        assertThat(batch.results()).isEmpty(); assertThat(batch.sourceWarningCode()).isNull();
    }
    @Test void cancellationDoesNotLaunchTheOtherSearch() {
        when(naver.search(KBA_QUERY)).thenThrow(new SearchFailure("SEARCH_INTERRUPTED"));
        assertThatThrownBy(() -> client.search(QUERY)).hasMessage("SEARCH_INTERRUPTED");
        verify(naver, never()).search(QUERY);
    }
    @Test void validatesBeforeDispatchAndKeepsBothProviderQueriesWithin300Characters() {
        assertThatThrownBy(() -> client.search(" ")).hasMessage("INVALID_QUERY");
        assertThatThrownBy(() -> client.search("가".repeat(301))).hasMessage("INVALID_QUERY");
        verify(naver, never()).search(anyString());
        when(naver.search(anyString())).thenAnswer(call -> {
            assertThat((String) call.getArgument(0)).hasSizeLessThanOrEqualTo(300).contains("변호사");
            return List.of();
        });
        client.search("민사" + "가".repeat(298));
        verify(naver, times(2)).search(anyString());
        when(naver.configured()).thenReturn(false);
        assertThatThrownBy(() -> client.search(QUERY)).hasMessage("SEARCH_NOT_CONFIGURED");
        verify(naver, times(2)).search(anyString());
    }
}
