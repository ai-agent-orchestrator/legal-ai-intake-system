package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.LegalChecklistResponse;
import com.ohgiraffers.handlermethod.dto.LegalFormTemplateResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureDetailResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureListResponse;
import com.ohgiraffers.handlermethod.dto.LegalProcedureStepResponse;
import com.ohgiraffers.handlermethod.entity.LegalFormTemplate;
import com.ohgiraffers.handlermethod.entity.LegalProcedure;
import com.ohgiraffers.handlermethod.legal.LegalProcedureDomain;
import com.ohgiraffers.handlermethod.repository.LegalFormTemplateRepository;
import com.ohgiraffers.handlermethod.repository.LegalProcedureRepository;
import com.ohgiraffers.handlermethod.repository.LegalProcedureStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class LegalProcedureService {

    /*
       Service는 "Controller와 Repository 사이의 업무 처리 구간"이다.

       Controller:
       HTTP 요청을 받는다.

       Service:
       어떤 Repository에서 무엇을 조회할지 결정한다.
       Entity를 그대로 내보내지 않고 Response DTO로 바꾼다.

       Repository:
       실제 DB 조회를 담당한다.
     */
    private final LegalProcedureRepository legalProcedureRepository;
    private final LegalProcedureStepRepository legalProcedureStepRepository;
    private final LegalFormTemplateRepository legalFormTemplateRepository;

    public LegalProcedureService(LegalProcedureRepository legalProcedureRepository,
                                 LegalProcedureStepRepository legalProcedureStepRepository,
                                 LegalFormTemplateRepository legalFormTemplateRepository) {
        this.legalProcedureRepository = legalProcedureRepository;
        this.legalProcedureStepRepository = legalProcedureStepRepository;
        this.legalFormTemplateRepository = legalFormTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<LegalProcedureListResponse> findProcedures(String domain) {
        /*
           절차 목록 조회.

           domain 이 없으면:
           전체 목록 조회

           domain 이 있으면:
           CIVIL / CRIMINAL / FAMILY / ELECTRONIC_LITIGATION 중 하나만 조회

           목록 화면에서는 보통 상세 단계까지 다 필요하지 않다.
           그래서 LegalProcedureListResponse 로 가볍게 내려준다.
         */
        List<LegalProcedure> procedures;

        if (domain == null || domain.isBlank()) {
            procedures = legalProcedureRepository.findAll();
        } else {
            procedures = legalProcedureRepository.findByDomainOrderByCode(parseDomain(domain));
        }

        return procedures.stream()
                /*
                   Entity -> DTO 변환.

                   Entity는 DB 테이블과 가까운 객체고,
                   DTO는 API 응답 JSON에 가까운 객체다.

                   DB 내부 구조를 그대로 노출하지 않고,
                   화면에 필요한 모양으로 바꿔서 내보낸다.
                 */
                .map(LegalProcedureListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LegalProcedureDetailResponse findProcedureDetail(String code) {
        /*
           절차 상세 조회.

           code 예시:
           civil-litigation
           criminal-supplementary-investigation

           상세 화면은 절차 기본 정보만으로 부족하다.
           그래서 procedure + steps + forms 를 묶어서 하나의 응답으로 만든다.
         */
        LegalProcedure procedure = findProcedure(code);

        List<LegalProcedureStepResponse> steps = legalProcedureStepRepository.findByProcedureOrderByStepOrder(procedure)
                .stream()
                .map(LegalProcedureStepResponse::from)
                .toList();

        List<LegalFormTemplateResponse> forms = legalFormTemplateRepository.findByProcedureOrderByFormCode(procedure)
                .stream()
                .map(LegalFormTemplateResponse::from)
                .toList();

        return LegalProcedureDetailResponse.of(procedure, steps, forms);
    }

    @Transactional(readOnly = true)
    public List<LegalProcedureStepResponse> findProcedureSteps(String code) {
        /*
           특정 절차의 단계만 조회한다.

           프론트에서 상세 화면 일부만 갱신하거나,
           챗봇이 "다음 단계만 알려줘" 같은 요청을 처리할 때 분리 API로 쓸 수 있다.
         */
        LegalProcedure procedure = findProcedure(code);

        return legalProcedureStepRepository.findByProcedureOrderByStepOrder(procedure)
                .stream()
                .map(LegalProcedureStepResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LegalFormTemplateResponse> findForms(String procedureCode) {
        /*
           양식 조회.

           procedureCode 가 없으면 전체 양식.
           procedureCode 가 있으면 특정 절차에 연결된 양식만 조회.

           예:
           /api/legal/forms?procedure=civil-litigation
         */
        List<LegalFormTemplate> forms;

        if (procedureCode == null || procedureCode.isBlank()) {
            forms = legalFormTemplateRepository.findAll();
        } else {
            forms = legalFormTemplateRepository.findByProcedureOrderByFormCode(findProcedure(procedureCode));
        }

        return forms.stream()
                .map(LegalFormTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LegalFormTemplateResponse findForm(String formCode) {
        /*
           formCode 로 양식 하나를 찾는다.

           Optional.orElseThrow()
           -> DB에 없으면 예외를 던진다.
           -> ApiExceptionHandler가 있으면 공통 에러 JSON으로 바꿀 수 있다.
         */
        return legalFormTemplateRepository.findByFormCode(formCode)
                .map(LegalFormTemplateResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Unknown legal form code: " + formCode));
    }

    public LegalChecklistResponse supplementaryInvestigationChecklist() {
        /*
           지금은 하드코딩된 prototype checklist 다.

           의미:
           "형사 보완수사 단계에서는 무엇을 챙겨야 하는가?"

           나중에 확장하면:
           DB checklist
           -> ontology rule
           -> chatbot response
           로 바꿀 수 있다.
         */
        return new LegalChecklistResponse(
                "CRIMINAL",
                "COMPLAINANT",
                "SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER",
                "검찰 송치 후 보완수사 단계",
                List.of(
                        "보완수사 요구 취지 확인",
                        "추가 증거 정리",
                        "시간순 사건표 보강",
                        "쟁점별 증거표 작성",
                        "공문서, 녹취, 메시지, 계좌자료 등 객관 증거 묶음 정리"
                ),
                "PREPARE_SUPPLEMENTARY_EVIDENCE_PACKET"
        );
    }

    private LegalProcedure findProcedure(String code) {
        /*
           code 로 LegalProcedure 하나를 찾는 공통 helper.

           여러 메서드에서 같은 조회 로직이 반복되므로 private 메서드로 뺐다.
         */
        return legalProcedureRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown legal procedure code: " + code));
    }

    private LegalProcedureDomain parseDomain(String domain) {
        /*
           URL 에서 들어온 문자열을 enum 으로 바꾼다.

           "civil" -> CIVIL
           "CIVIL" -> CIVIL

           enum 으로 바꾸면 오타가 줄고, 정해진 domain 만 처리할 수 있다.
         */
        return LegalProcedureDomain.valueOf(domain.trim().toUpperCase(Locale.ROOT));
    }
}
