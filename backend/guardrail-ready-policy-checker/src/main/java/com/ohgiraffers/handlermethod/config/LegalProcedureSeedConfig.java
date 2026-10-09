package com.ohgiraffers.handlermethod.config;

import com.ohgiraffers.handlermethod.entity.LegalFormTemplate;
import com.ohgiraffers.handlermethod.entity.LegalProcedure;
import com.ohgiraffers.handlermethod.entity.LegalProcedureStep;
import com.ohgiraffers.handlermethod.legal.LegalProcedureDomain;
import com.ohgiraffers.handlermethod.repository.LegalFormTemplateRepository;
import com.ohgiraffers.handlermethod.repository.LegalProcedureRepository;
import com.ohgiraffers.handlermethod.repository.LegalProcedureStepRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class LegalProcedureSeedConfig {

    /*
       SeedConfig는 앱이 시작될 때 공부용 초기 데이터를 DB에 넣는 설정이다.

       현재 DB는 H2 in-memory 이고 ddl-auto=create-drop 이라서
       서버를 새로 켤 때마다 테이블이 새로 만들어진다.

       그래서 법률 절차 예시 데이터를 자동으로 넣어야
       Postman에서 바로 GET /api/legal/procedures 를 테스트할 수 있다.
     */
    @Bean
    CommandLineRunner legalProcedureSeedRunner(LegalProcedureRepository procedureRepository,
                                               LegalProcedureStepRepository stepRepository,
                                               LegalFormTemplateRepository formRepository) {
        /*
           CommandLineRunner:
           Spring Boot 애플리케이션이 뜬 직후 실행되는 코드다.

           여기서는 서버 시작 직후 seed(...)를 호출해서
           legal_procedure / legal_procedure_step / legal_form_template 테이블에
           예시 데이터를 넣는다.
         */
        return args -> seed(procedureRepository, stepRepository, formRepository);
    }

    @Transactional
    public void seed(LegalProcedureRepository procedureRepository,
                     LegalProcedureStepRepository stepRepository,
                     LegalFormTemplateRepository formRepository) {
        /*
           이미 데이터가 있으면 다시 넣지 않는다.
           중복 insert를 막는 보호장치다.
         */
        if (procedureRepository.count() > 0) {
            return;
        }

        /*
           1. 전자소송 이용 절차

           LegalProcedure = 큰 절차 하나
           LegalProcedureStep = 그 절차 안의 단계
           LegalFormTemplate = 그 절차와 관련된 양식/포털 안내
         */
        LegalProcedure electronicLitigation = procedureRepository.save(
                LegalProcedure.create(
                        "electronic-litigation",
                        LegalProcedureDomain.ELECTRONIC_LITIGATION,
                        "전자소송 이용 절차",
                        "사용자 등록, 소 제기, 답변서 제출, 송달, 사건기록열람으로 이어지는 온라인 소송 이용 흐름입니다.",
                        "party-or-representative"
                )
        );

        stepRepository.save(LegalProcedureStep.create(
                electronicLitigation,
                1,
                "user-registration",
                "사용자 등록",
                "실지명의 확인 가능한 인증서로 본인을 확인하고 전자소송 사용자 정보를 등록합니다.",
                "인증서 준비|사용자 정보 입력|전자소송 동의 여부 확인"
        ));
        stepRepository.save(LegalProcedureStep.create(
                electronicLitigation,
                2,
                "claim-filing",
                "소 제기",
                "원고는 전자소송 포털에서 소장을 작성하고 전자서명 후 제출합니다.",
                "사건 유형 확인|소장 작성|증거자료 첨부|전자서명 후 제출"
        ));
        stepRepository.save(LegalProcedureStep.create(
                electronicLitigation,
                3,
                "record-review",
                "사건기록열람",
                "전자소송에 동의한 당사자는 진행 중인 사건 기록을 온라인으로 열람할 수 있습니다.",
                "사건번호 확인|기록 열람|제출 서류와 송달 문서 확인"
        ));

        formRepository.save(LegalFormTemplate.create(
                electronicLitigation,
                "electronic-litigation-consent",
                "전자소송 동의 관련 안내",
                "portal-process",
                "전자소송 포털에서 사건번호와 인증정보를 기준으로 동의 절차를 진행할 때 참고합니다."
        ));

        /*
           2. 민사소송 절차

           목록 화면:
           civil-litigation 이라는 code 로 보인다.

           상세 화면:
           소장 작성, 답변서와 쟁점 정리, 변론과 판결 단계가 함께 나온다.
         */
        LegalProcedure civilLitigation = procedureRepository.save(
                LegalProcedure.create(
                        "civil-litigation",
                        LegalProcedureDomain.CIVIL,
                        "민사소송 절차",
                        "권리관계나 금전 분쟁을 법원 절차로 해결하기 위한 소 제기, 답변, 변론, 판결 중심의 절차입니다.",
                        "plaintiff-or-defendant"
                )
        );

        stepRepository.save(LegalProcedureStep.create(
                civilLitigation,
                1,
                "complaint-preparation",
                "소장 작성",
                "청구취지, 청구원인, 당사자, 증거를 정리하여 소장을 작성합니다.",
                "청구취지 정리|청구원인 정리|피고 정보 확인|핵심 증거 목록 작성"
        ));
        stepRepository.save(LegalProcedureStep.create(
                civilLitigation,
                2,
                "answer-and-issues",
                "답변서와 쟁점 정리",
                "피고는 답변서를 제출하고, 양측은 주장과 증거의 쟁점을 정리합니다.",
                "상대방 주장 확인|인정/부인 사실 구분|쟁점표 작성|증거와 주장 연결"
        ));
        stepRepository.save(LegalProcedureStep.create(
                civilLitigation,
                3,
                "hearing-and-judgment",
                "변론과 판결",
                "법원은 제출된 주장과 증거를 기초로 변론을 진행하고 판결합니다.",
                "기일 확인|준비서면 제출|증거 설명 준비|판결 선고일 확인"
        ));

        formRepository.save(LegalFormTemplate.create(
                civilLitigation,
                "litigation-aid-application-a1330",
                "소송구조신청서",
                "pdf",
                "소송비용을 지출할 자금능력이 부족한 경우 법원에 소송구조를 신청할 때 사용하는 양식입니다."
        ));

        /*
           3. 가사 절차구조 제도

           절차구조는 가사비송사건에서 비용 부담이 어려울 때 쓰는 제도다.
           나중에 family domain 의 checklist/양식 API로 확장하기 좋다.
         */
        LegalProcedure familyProcedureAid = procedureRepository.save(
                LegalProcedure.create(
                        "family-procedure-aid",
                        LegalProcedureDomain.FAMILY,
                        "가사 절차구조 제도",
                        "가사비송사건에서 절차비용을 부담하기 어려운 사람이 비용 유예 또는 면제를 신청하는 절차입니다.",
                        "applicant"
                )
        );

        stepRepository.save(LegalProcedureStep.create(
                familyProcedureAid,
                1,
                "eligibility-check",
                "무자력 요건 확인",
                "신청인이 절차비용을 지출하면 생활에 현저한 지장이 있는지 확인합니다.",
                "수급자 여부 확인|차상위계층 여부 확인|한부모가족 지원대상 여부 확인|기초연금/장애인연금 여부 확인"
        ));
        stepRepository.save(LegalProcedureStep.create(
                familyProcedureAid,
                2,
                "document-preparation",
                "소명자료 준비",
                "재산관계진술서와 가족관계, 재산내역 관련 자료를 준비합니다.",
                "재산관계진술서 작성|주민등록등본 또는 가족관계증명서 준비|예금/자동차/부동산 자료 확인"
        ));

        formRepository.save(LegalFormTemplate.create(
                familyProcedureAid,
                "family-procedure-aid-application",
                "절차구조신청서",
                "hwp",
                "가사비송사건에서 절차구조를 신청할 때 사용하는 양식입니다."
        ));

        /*
           4. 형사 보완수사 대응

           고소인 관점에서 보완수사 단계에 무엇을 준비해야 하는지
           체크리스트형 지식 데이터로 넣어 둔다.
         */
        LegalProcedure criminalSupplementary = procedureRepository.save(
                LegalProcedure.create(
                        "criminal-supplementary-investigation",
                        LegalProcedureDomain.CRIMINAL,
                        "형사 보완수사 대응",
                        "검찰 송치 후 보완수사 단계에서 고소인이 추가 증거와 쟁점을 정리하는 절차입니다.",
                        "complainant"
                )
        );

        stepRepository.save(LegalProcedureStep.create(
                criminalSupplementary,
                1,
                "request-review",
                "보완수사 취지 확인",
                "보완수사가 내려온 이유와 추가 확인이 필요한 쟁점을 정리합니다.",
                "보완수사 요구 취지 확인|수사기관 연락 기록 정리|부족한 쟁점 표시"
        ));
        stepRepository.save(LegalProcedureStep.create(
                criminalSupplementary,
                2,
                "evidence-packet",
                "추가 증거 패킷 준비",
                "공문서, 녹취, 메시지, 계좌자료, 시간순 사건표를 묶어 추가 제출 자료를 준비합니다.",
                "시간순 사건표 보강|증거번호 부여|쟁점별 증거표 작성|객관 증거와 주장 연결"
        ));
        stepRepository.save(LegalProcedureStep.create(
                criminalSupplementary,
                3,
                "supplementary-submission",
                "보완자료 제출",
                "수사기관이 확인하기 쉽게 쟁점, 증거, 설명을 분리하여 제출합니다.",
                "제출자료 목록 작성|핵심 주장 요약|증거 원본 보관|제출일 기록"
        ));
    }
}
