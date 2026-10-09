package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.LegalChecklistResponse;
import com.ohgiraffers.handlermethod.dto.LegalFormTemplateResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureDetailResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureListResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureStepResponse;
import com.ohgiraffers.handlermethod.service.LegalProcedureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/legal")
public class LegalProcedureController {

    /*
       Controller는 "HTTP 주소를 Java 메서드에 연결하는 입구"다.

       React/Postman:
       GET /api/legal/procedures

       Spring:
       LegalProcedureController.procedures()

       즉 여기서는 법률 절차 지식 베이스를 조회할 수 있는 API 주소를 만든다.
       실제 DB 조회와 판단은 Service에 맡긴다.
     */
    private final LegalProcedureService legalProcedureService;

    public LegalProcedureController(LegalProcedureService legalProcedureService) {
        this.legalProcedureService = legalProcedureService;
    }

    @GetMapping("/procedures")
    public List<LegalProcedureListResponse> procedures(@RequestParam(required = false) String domain) {
        /*
           목록 조회 API.

           요청 예시:
           GET /api/legal/procedures
           -> 전체 법률 절차 목록 조회

           GET /api/legal/procedures?domain=CIVIL
           -> 민사 절차만 조회

           @RequestParam 은 URL 뒤의 ?domain=CIVIL 같은 쿼리 파라미터를 받는다.
           required = false 이므로 domain 이 없어도 요청 가능하다.
         */
        return legalProcedureService.findProcedures(domain);
    }

    @GetMapping("/procedures/{code}")
    public LegalProcedureDetailResponse procedureDetail(@PathVariable String code) {
        /*
           상세 조회 API.

           요청 예시:
           GET /api/legal/procedures/civil-litigation

           @PathVariable 은 주소 안에 들어간 값을 꺼낸다.
           여기서는 civil-litigation 이 code 로 들어온다.

           React Router의 useParams()와 거의 같은 역할이다.
         */
        return legalProcedureService.findProcedureDetail(code);
    }

    @GetMapping("/procedures/{code}/steps")
    public List<LegalProcedureStepResponse> procedureSteps(@PathVariable String code) {
        /*
           특정 절차에 딸린 단계만 따로 조회한다.

           예:
           GET /api/legal/procedures/civil-litigation/steps
           -> 민사소송 절차의 단계 목록만 반환
         */
        return legalProcedureService.findProcedureSteps(code);
    }

    @GetMapping("/forms")
    public List<LegalFormTemplateResponse> forms(@RequestParam(required = false) String procedure) {
        /*
           양식 목록 조회 API.

           GET /api/legal/forms
           -> 전체 양식 조회

           GET /api/legal/forms?procedure=civil-litigation
           -> 민사소송 절차에 연결된 양식만 조회
         */
        return legalProcedureService.findForms(procedure);
    }

    @GetMapping("/forms/{formCode}")
    public LegalFormTemplateResponse form(@PathVariable String formCode) {
        /*
           양식 하나를 formCode 로 상세 조회한다.

           예:
           GET /api/legal/forms/litigation-aid-application-a1330
         */
        return legalProcedureService.findForm(formCode);
    }

    @GetMapping("/criminal/checklist/supplementary-investigation")
    public LegalChecklistResponse supplementaryInvestigationChecklist() {
        /*
           형사 보완수사 단계처럼 실무적으로 자주 필요한 체크리스트는
           바로 꺼내 쓸 수 있는 전용 API로 둔다.

           나중에는 이 값도 DB/ontology 기반으로 확장할 수 있다.
         */
        return legalProcedureService.supplementaryInvestigationChecklist();
    }
}
