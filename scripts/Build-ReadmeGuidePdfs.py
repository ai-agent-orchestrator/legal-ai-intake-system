"""Three concise dark-tablet PDF guides linked from README. Edit CONTENT below."""
from pathlib import Path
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/presentation-pdfs'
CONTENT=[
('COST_CONTROL_DARK_TABLET.pdf','Cost Control','사용한 단계는 정산하고, 실행하지 않은 예약은 해제합니다.',[
('01 입력·출력 범위 확인','입력 길이와 출력 토큰 상한을 확인합니다. 입력 Guard·생성·출력 Guard·검색문·설명은 비용 단계를 구분합니다.'),
('02 예산 확인 → 단계 예약','모델별 단가와 최대 사용량으로 예약합니다. 정산·추정 사용·진행 예약을 월·일·세션 예산과 비교합니다.'),
('03 호출 → usage 확인 → 한 번만 종료','usage가 확인되면 토큰 기준 정산, 실행 후 usage 미확인은 추정 정산합니다. 완료·오류 콜백의 중복 종료를 막습니다.'),
('04 차단·중단 후에도 비용 확인','출력 차단 이전의 생성 비용은 유지합니다. SSE 취소·오류에서 공급자 중단과 정산을 처리하고 예약일 기준으로 일 비용을 귀속합니다.'),
('05 C16에서 관측·공급자와 대조','실사용·추정·정산·예약 및 단계별 비용을 구분합니다. 메모리 장부는 영속 청구 원장이 아니며 실제 공급자 사용량과 별도 대조합니다.')],
'근거: docs/diagrams/03_COST_CONTROL_FLOW.md · docs/SSE_COST_LIFECYCLE_REVIEW.md'),
('ERROR_STOP_FLOW_DARK_TABLET.pdf','Error Stop Flow','실패한 경계를 확인하고, 저장·호출·재시도를 안전하게 멈춥니다.',[
('01 인증·권한 → 401 / 403','로그인 없음·만료는 401, 인증 후 접근 권한 부족은 403으로 구분합니다. 토큰·소유자·배정 여부를 확인합니다.'),
('02 입력·대상 → 400 / 404','필수값·길이·코드·URL 등 입력 검증과 대상 ID 존재·연결 관계를 확인합니다. 검증 실패 후 저장이 없는지 확인합니다.'),
('03 중복·동시 수정 → 409','같은 요청 키의 다른 본문, 오래된 버전, 금지된 상태 전이를 구분합니다. 입력을 보존하고 최신 상태를 확인합니다.'),
('04 AI 정책·예산 → 안전 응답','정책 차단은 HTTP 상태만으로 판단하지 않고 blocked·reasonCode·detailCode를 함께 확인합니다. 다음 LLM 단계 호출을 중단합니다.'),
('05 외부 오류·응답 유실 → 재조회','통신 실패를 미처리로 단정하지 않습니다. 무조건 재전송하지 말고 기존 ID·처리 상태·정산·예약 잔여를 확인합니다.')],
'근거: docs/BACKEND_DRAFT_FAILURE_FLOW.mmd · docs/SSE_COST_LIFECYCLE_REVIEW.md'),
('DYNAMIC_GUARD_DARK_TABLET.pdf','Dynamic Guard','요청 한 건의 위험 패턴과 대화 누적 위험을 나란히 봅니다.',[
('01 요청 패턴 | 위험 신호 조합','차단행동 반복·위험행동 반복·외부전송 위험 재시도를 관찰합니다. 같은 요청의 신호를 점수화하며 80점 이상은 차단 권고입니다.'),
('02 대화 누적 | 같은 세션의 차단 이력','새 요청 전에 세션 차단 여부를 확인합니다. 누적 대상 차단 이벤트가 발생하면 점수·총횟수·사유별 횟수를 함께 갱신합니다.'),
('03 사유별 가중치','법률 위해 +70 / 탈옥·주입 +60 / 비밀·개인정보·인증 우회·입력 Guard +50 / 길이·비용 관련 +35 / 범위 밖 +20.'),
('04 실제 세션 제한 조건','누적 100점, 고위험 동일 사유 2회, 길이 차단 2회, 범위 밖 3회 또는 전체 차단 3회 중 해당 조건으로 제한합니다. 요청 권고 점수와 합산하지 않습니다.'),
('05 화면13 | 지표 → 이력 → 점검','C16 실제 지표·시계열과 C17 위험 이력을 함께 봅니다. 세션 상태는 메모리이며 정상 요청으로 자동 감점되지 않고 재시작 시 초기화됩니다.')],
'근거: docs/diagrams/02B2_DYNAMIC_GUARD_FLOW.md · docs/SCREEN13_OBSERVABILITY_REVIEW.md')]

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    pdfmetrics.registerFont(TTFont('K','C:/Windows/Fonts/malgun.ttf'))
    pdfmetrics.registerFont(TTFont('KB','C:/Windows/Fonts/malgunbd.ttf'))
    for filename,title,lead,steps,note in CONTENT:
        c=canvas.Canvas(str(OUT/filename),pagesize=(1024,768))
        c.setTitle(title+' | Legal AI Intake System')
        c.setFillColor(HexColor('#0e1725')); c.rect(0,0,1024,768,fill=1,stroke=0)
        def text(s,x,y,w,size=17,color='#e8eff7',bold=False):
            p=Paragraph(escape(s),ParagraphStyle('p',fontName='KB' if bold else 'K',fontSize=size,leading=size*1.45,textColor=HexColor(color),wordWrap='CJK'))
            _,h=p.wrap(w,768); p.drawOn(c,x,768-y-h); return h
        text('LEGAL AI INTAKE / OPERATIONS GUIDE',42,24,940,12,'#6bd6e8',True)
        text(title,42,62,940,36,bold=True)
        text(lead,44,123,936,18,'#adbed2')
        for i,(heading,body) in enumerate(steps):
            y=180+i*98
            c.setFillColor(HexColor('#162438')); c.setStrokeColor(HexColor('#2b405b'))
            c.roundRect(44,768-y-84,936,84,10,fill=1,stroke=1)
            text(heading,62,y+9,900,19,'#6bd6e8',True)
            h=text(body,62,y+40,900,15)
            assert h<=44,(filename,heading,h)
            if i<4:
                c.setStrokeColor(HexColor('#6bd6e8')); c.line(512,768-y-85,512,768-y-96)
        text(note,44,686,936,11,'#adbed2')
        text('2026-10-25 | 4:3 DARK TABLET | 설계 요약 - 최신 실행 검증 결과는 별도 확인',44,724,936,11,'#adbed2')
        c.save(); print(OUT/filename)

if __name__=='__main__':
    # Keep the old command usable without overwriting evidence with summary-only pages.
    import runpy
    runpy.run_path(str(Path(__file__).with_name('Build-ExperimentEvidencePdfs.py')), run_name='__main__')
