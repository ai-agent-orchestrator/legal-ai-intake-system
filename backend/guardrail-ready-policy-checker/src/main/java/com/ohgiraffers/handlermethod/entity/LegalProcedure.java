package com.ohgiraffers.handlermethod.entity;

import com.ohgiraffers.handlermethod.legal.LegalProcedureDomain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "legal_procedure")
public class LegalProcedure {

    /*
       Entity는 DB 테이블과 연결되는 Java 객체다.

       이 클래스는 legal_procedure 테이블이 된다.

       한 row 예시:
       code = civil-litigation
       domain = CIVIL
       title = 민사소송 절차
       summary = 민사소송 흐름 설명
       userRole = plaintiff-or-defendant

       React 목록 화면에서는 이 row 들을 조회해서 절차 목록으로 보여준다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
       code 는 사람이 읽을 수 있는 고유 식별자다.

       DB 내부 PK:
       id = 1

       API/URL 에서 쓰는 식별자:
       code = civil-litigation

       React 주소:
       /legal/procedures/civil-litigation
     */
    @Column(nullable = false, unique = true, length = 80)
    private String code;

    /*
       domain 은 민사/형사/가사/전자소송 같은 큰 분류다.
       문자열을 아무거나 저장하지 않고 enum 으로 제한한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LegalProcedureDomain domain;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, length = 120)
    private String userRole;

    protected LegalProcedure() {
        /*
           JPA가 Entity를 만들 때 필요한 기본 생성자다.
           외부 코드에서 마음대로 new 하지 못하게 protected 로 둔다.
         */
    }

    private LegalProcedure(String code,
                           LegalProcedureDomain domain,
                           String title,
                           String summary,
                           String userRole) {
        this.code = code;
        this.domain = domain;
        this.title = title;
        this.summary = summary;
        this.userRole = userRole;
    }

    public static LegalProcedure create(String code,
                                        LegalProcedureDomain domain,
                                        String title,
                                        String summary,
                                        String userRole) {
        /*
           생성 의미를 분명하게 하기 위한 정적 팩토리 메서드다.

           seed data:
           LegalProcedure.create(...)

           이렇게 쓰면 "법률 절차 row를 하나 만든다"는 의도가 잘 보인다.
         */
        return new LegalProcedure(code, domain, title, summary, userRole);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public LegalProcedureDomain getDomain() {
        return domain;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getUserRole() {
        return userRole;
    }
}
