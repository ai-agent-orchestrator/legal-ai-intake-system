package com.ohgiraffers.handlermethod.search;

import com.ohgiraffers.handlermethod.cost.*;
import com.ohgiraffers.handlermethod.entity.LawyerSearchCondition;
import com.ohgiraffers.handlermethod.guardrail.*;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.riskengine.*;
import com.ohgiraffers.handlermethod.support.AiMetricRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SearchLlmServiceTest {
    final LlmClient llm = mock(LlmClient.class);
    final InputGuardrailClient input = mock(InputGuardrailClient.class);
    final OutputGuardrailClient output = mock(OutputGuardrailClient.class);
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final LawyerSearchCondition condition = LawyerSearchCondition.create(1L, "민사", "대여금 분쟁", "서울특별시",
            "법무법인", "5년 이상", "상관없음", "전화 상담", "KBA_NAVER_PUBLIC_WEB", "saved");
    SearchLlmService service(AiCostControlService cost) {
        return new SearchLlmService(llm, new ObjectMapper(), new CostedGuardrailService(input, output, cost,
                "fixture", BigDecimal.ZERO, BigDecimal.ZERO, 10, 10), cost, new LegalFixedGuardService(),
                new LegalDynamicGuardService(registry), new PrivacyOutputGuardService(), new AiMetricRecorder(registry),
                "fixture", BigDecimal.ONE, BigDecimal.ONE);
    }
    AiCostControlService cost(BigDecimal budget) { return new AiCostControlService(budget, budget, budget, BigDecimal.ONE,
            BigDecimal.ONE, 256, 1, 10, registry); }
    @Test void zeroBudgetPreventsLlmDispatchAndLeavesNoReservation() {
        var cost = cost(BigDecimal.ZERO);
        when(input.check(anyString())).thenAnswer(call -> new InputGuardrailResult("PASSED", call.getArgument(0), "INPUT", new GuardrailUsage(0, 0)));
        assertThatThrownBy(() -> service(cost).query(condition, "search:budget", () -> true)).isInstanceOf(CostLimitException.class);
        verifyNoInteractions(llm); assertThat(cost.dailyCost(LocalDate.now()).reserved()).isZero();
    }
    @Test void guardModifiedContentReachesLlmAndReportedUsageSettlesBeforeParsing() {
        var cost = cost(BigDecimal.TEN);
        when(input.check(anyString())).thenReturn(new InputGuardrailResult("MODIFIED", "{\"legalField\":\"민사\"}", "INPUT", new GuardrailUsage(0, 0)));
        when(llm.chat(anyString(), anyString())).thenReturn(new LlmClient.LlmResponse("{\"query\":\"민사 대여금 변호사\"}", "fixture", 10, 5));
        when(output.check(anyString())).thenAnswer(call -> new OutputGuardrailResult("PASSED", call.getArgument(0), "OUTPUT", new GuardrailUsage(0, 0)));
        assertThat(service(cost).query(condition, "search:modified", () -> true)).isEqualTo("민사 대여금 변호사");
        verify(llm).chat(anyString(), eq("{\"legalField\":\"민사\"}"));
        assertThat(cost.dailyCost(LocalDate.now()).reported()).isPositive(); assertThat(cost.dailyCost(LocalDate.now()).reserved()).isZero();
    }
}
