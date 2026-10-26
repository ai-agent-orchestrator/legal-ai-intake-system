"""Deduplicate the supplied report, add vector flowcharts, and append the approved retrospective."""
from pathlib import Path
from io import BytesIO
from hashlib import sha256
from xml.sax.saxutils import escape
import argparse, ast, json, math, re
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor
from pypdf import PdfReader, PdfWriter, Transformation
from pypdf.annotations import Link

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description="원본 V2 보고서를 README 순서·전체 Flow·회고 통합본으로 재편집합니다.")
parser.add_argument("--source-pdf", type=Path, required=True, help="97쪽 원본 V2 보고서 PDF 경로")
parser.add_argument("--output", type=Path, default=ROOT / "output/pdf/LEGAL_AI_INTAKE_TEAM_PROJECT_REPORT_FINAL_2026-10-26.pdf")
parser.add_argument("--font-dir", type=Path, default=Path("C:/Windows/Fonts"), help="malgun.ttf, malgunbd.ttf, consola.ttf 폴더")
args = parser.parse_args()
ORIGINAL = args.source_pdf.resolve()
if not ORIGINAL.is_file():
    parser.error("--source-pdf 파일이 없습니다.")
OUT = args.output.resolve()
QA = ROOT / "tmp/pdfs/report-revision-v4"
AUTHORED = QA / "authored.pdf"
STRUCTURE = ROOT / "docs/presentation-2026-10-25/FINAL_REPORT_STRUCTURE.md"
W, H = 1280, 960
BG, PANEL, LINE, TEXT, MUTED = "#0e1725", "#162438", "#2b405b", "#e8eff7", "#adbed2"
BLUE, CYAN, GREEN, GOLD, RED = "#7bb6ff", "#6bd6e8", "#70dfb2", "#eac778", "#ff9eaa"
SECTIONS = [
    ("01", "PM Positioning"), ("02", "PM Multi-Layer Analysis"),
    ("03", "Problem → Solution"), ("04", "Security Architecture"),
    ("05", "차단 이유를 읽을 수 있어야 합니다"), ("06", "Product Introduction"),
    ("07", "전체 시스템·핵심 기능 Flow"), ("08", "핵심 백엔드 Flow"),
    ("09", "핵심 API·전체 API 지도"), ("10", "쉬운 운영 관리·마케팅"),
    ("11", "실시간 보안 관측·대응"), ("12", "Current Implementation·수행 방법"),
    ("13", "Core Message"), ("14", "이번 설계의 의미"),
    ("A", "부록 A · ERD·데이터 제약"), ("B", "부록 B · 화면01~13·모바일"),
    ("C", "부록 C · DDD·정책·코드 매핑"), ("D", "부록 D · 검증 결과·대표 증거"),
    ("15", "회고·시연 배포 체크리스트·확장 계획"),
]
NAMES = dict(SECTIONS)
QA.mkdir(parents=True, exist_ok=True)
OUT.parent.mkdir(parents=True, exist_ok=True)
for name, font in [("KR", "malgun.ttf"), ("KB", "malgunbd.ttf"), ("Mono", "consola.ttf")]:
    pdfmetrics.registerFont(TTFont(name, str(args.font_dir / font)))
c = canvas.Canvas(str(AUTHORED), pagesize=(W, H))
c.setTitle("팀 프로젝트 결과보고서 · 통합 개정본")
parts, bounds, notes, graphs = [], [], [], []
authored_page = 0
opened = False
original_reader = PdfReader(ORIGINAL)
if len(original_reader.pages) != 97:
    parser.error("이 편집본은 97쪽 V2 원본을 기준으로 합니다.")
original_rows = []
for number, page in enumerate(original_reader.pages, 1):
    original_text = page.extract_text() or ""
    original_lines = [line.strip() for line in original_text.splitlines() if line.strip()]
    original_titles = [line for line in original_lines[:8] if not re.search(r"LEGAL AI INTAKE /|TEAM PROJECT REPORT|근거:", line)][:3]
    original_rows.append(dict(page=number, title=" / ".join(original_titles), text=original_text))


def clean(value):
    value = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", str(value))
    return value.replace(chr(96), "").replace("**", "").replace("<br/>", "\n").replace("<br>", "\n").replace(r"\|", "|")


def para(value, width, size=16, color=TEXT, bold=False, align=0):
    markup = escape(clean(value)).replace("\\", '<font name="Mono">\\</font>').replace("\n", "<br/>")
    p = Paragraph(markup, ParagraphStyle(
        "p", fontName="KB" if bold else "KR", fontSize=size, leading=size*1.42,
        textColor=HexColor(color), wordWrap="CJK", splitLongWords=True, alignment=align,
    ))
    _, height = p.wrap(width, 6000)
    return p, height


def text(value, x, y, width, size=16, color=TEXT, bold=False, align=0):
    p, height = para(value, width, size, color, bold, align)
    assert 0 <= x and x+width <= W and 0 <= y and y+height <= 895, (authored_page, str(value)[:65], x, y, width, height)
    p.drawOn(c, x, H-y-height)
    bounds.append(dict(page=authored_page, x=x, y=y, width=width, height=height))
    return height


def end():
    global opened
    if opened:
        c.showPage()
        opened = False


def begin(section, title, lead="", source=""):
    global authored_page, opened
    end()
    authored_page += 1
    opened = True
    parts.append(dict(file=str(AUTHORED), page=authored_page-1, section=section, title=title, kind="authored"))
    c.setFillColor(HexColor(BG))
    c.rect(0, 0, W, H, fill=1, stroke=0)
    text("LEGAL AI INTAKE / INTEGRATED PROJECT REPORT", 48, 25, 1184, 12, CYAN, True)
    text(title, 48, 66, 1184, 30, TEXT, True)
    if lead:
        text(lead, 50, 119, 1180, 15, MUTED)
    if source:
        text("근거: " + source, 50, 875, 1180, 10, MUTED)
    notes.append("\n## " + title + "\n\n" + lead + "\n")


def box(x, y, width, height, title, body, color=CYAN, size=17):
    c.setFillColor(HexColor(PANEL))
    c.setStrokeColor(HexColor(color))
    c.setLineWidth(1.2)
    c.roundRect(x, H-y-height, width, height, 12, fill=1, stroke=1)
    th = text(title, x+17, y+12, width-34, size+1, color, True)
    bh = text(body, x+17, y+23+th, width-34, size)
    assert 23+th+bh <= height-9, (authored_page, title, height, th, bh)


def diamond(x, y, width, height, title, body):
    p = c.beginPath()
    p.moveTo(x+width/2, H-y)
    p.lineTo(x+width, H-y-height/2)
    p.lineTo(x+width/2, H-y-height)
    p.lineTo(x, H-y-height/2)
    p.close()
    c.setFillColor(HexColor("#302c22"))
    c.setStrokeColor(HexColor(GOLD))
    c.drawPath(p, fill=1, stroke=1)
    text(title, x+width*.1, y+height*.27, width*.8, 17, GOLD, True, 1)
    text(body, x+width*.1, y+height*.49, width*.8, 14, TEXT, False, 1)


def arrow(points, color=CYAN, dashed=False, label="", lx=None, ly=None, lw=220):
    c.setStrokeColor(HexColor(color))
    c.setLineWidth(1.8)
    c.setDash(5, 4) if dashed else c.setDash()
    for (x, y), (xx, yy) in zip(points, points[1:]):
        c.line(x, H-y, xx, H-yy)
    c.setDash()
    (x, y), (xx, yy) = points[-2:]
    a = math.atan2(yy-y, xx-x)
    for delta in (-.5, .5):
        c.line(xx, H-yy, xx-9*math.cos(a+delta), H-(yy-9*math.sin(a+delta)))
    if label:
        text(label, lx, ly, lw, 13, color)


def imported(section, pages, why):
    end()
    for n in pages:
        parts.append(dict(file=str(ORIGINAL), page=n-1, originalPage=n, section=section,
                          title=original_rows[n-1]["title"].split(" / ")[0], kind="original"))
    notes.append("\n원본 유지: " + ", ".join(map(str, pages)) + "쪽. " + why + "\n")


def tables(path):
    result, current = [], []
    for line in Path(path).read_text(encoding="utf-8").splitlines()+[""]:
        if line.startswith("|"):
            row = [s.strip() for s in re.split(r"(?<!\\)\|", line.strip()[1:-1])]
            if all(re.match(r"^:?-+:?$", s.replace(" ", "")) for s in row):
                continue
            current.append(row)
        elif current:
            result.append(current)
            current = []
    return result


def table(section, title, lead, headers, rows, widths, size=14, source=""):
    assert sum(widths) == 1184
    sequence, y = 0, 0
    def start():
        nonlocal sequence, y
        sequence += 1
        begin(section, title + (" · 계속" if sequence > 1 else ""), lead, source)
        y = 181
        hh = max(para(v, w-22, size+1)[1] for v, w in zip(headers, widths))+22
        c.setFillColor(HexColor("#203b52"))
        c.rect(48, H-y-hh, 1184, hh, fill=1, stroke=0)
        x = 48
        for v, w in zip(headers, widths):
            text(v, x+11, y+10, w-22, size+1, CYAN, True)
            x += w
        y += hh
    start()
    for index, row in enumerate(rows):
        assert len(row) == len(headers), (title, row)
        hh = max(para(v, w-22, size)[1] for v, w in zip(row, widths))+24
        assert hh < 620, (title, hh)
        if y+hh > 850:
            start()
        c.setFillColor(HexColor(PANEL if index % 2 == 0 else BG))
        c.rect(48, H-y-hh, 1184, hh, fill=1, stroke=0)
        c.setStrokeColor(HexColor(LINE))
        c.line(48, H-y-hh, 1232, H-y-hh)
        x = 48
        for value, w in zip(row, widths):
            text(value, x+11, y+10, w-22, size)
            x += w
        y += hh
    notes.append("| " + " | ".join(headers) + " |\n| " + " | ".join("---" for _ in headers) + " |\n" +
                 "\n".join("| " + " | ".join(clean(v).replace("\n", " / ").replace("|", r"\|") for v in row) + " |" for row in rows) + "\n")


def graph_note(name, statements):
    graphs.append(dict(name=name, stages=statements))
    notes.append("\n" + "\n".join("- " + s for s in statements) + "\n")


# Opening and chapter order follow the reviewed main README.
begin("cover", "팀 프로젝트 결과보고서", "Security-Aware Legal AI Intake System · 2026-10-26 통합 개정본")
text("선택형 법률 문의에서\n보안·비용 통제와\n운영 관측까지", 64, 238, 1120, 49, CYAN, True)
box(56, 525, 1168, 170, "전체 흐름을 먼저 보고, 코드와 검증 근거로 내려갑니다.",
    "README 순서의 제품 설명 + 전체·핵심 Flow + API·화면·데이터 매핑\n오늘의 시연·배포 회고와 앞으로의 확장 계획을 마지막 장으로 통합했습니다.", GREEN, 21)
text("중복 제품 소개·실험 안내 페이지는 통합하고, 고유한 데이터·화면·검증 결과는 본문과 부록에 나누어 보존했습니다.", 61, 753, 1150, 18, MUTED)
text("코드 기준 main / 64f7f62 · 과거 실험 캡처와 이번 회귀 검사 결과를 구분합니다.", 61, 813, 1150, 16, MUTED)
begin("contents", "목차", "README 순서로 읽고, 상세 매핑·화면·실험 근거는 부록에서 확인합니다.")
end()
imported("01", [3], "PM 포지셔닝을 한 번만 제시합니다.")
imported("02", [5], "여섯 관점의 분석을 한 번만 제시합니다.")
imported("03", [4], "반복되던 Problem–Solution 설명을 이 페이지로 통합합니다.")
imported("04", [6], "보안 통제의 책임을 요약합니다.")

begin("04", "보안 Flow · 어디서 멈추는가", "인증·입력·객체 권한·도메인 정책의 실패 경계가 서로 다릅니다.", "SecurityConfig / ApiExceptionHandler / IntakeAccessService / AiService")
stages = [
    (181, 89, "요청 진입 · HTTP 메서드·경로", "공개 경로 / 업무 API / Actuator 체인을 구분"),
    (316, 110, "인증·역할 정책", "공개 C01·C02·R01~R06·P06 / 업무 JWT / 지표 ADMIN Basic\nS05를 포함한 /api/agent/**는 ADMIN JWT"),
    (474, 110, "DTO·대상·소유·배정 확인", "입력 검증 → 대상 ID·연결 관계 → 문의 소유자·배정 LAWYER·ADMIN"),
    (632, 110, "업무 불변 조건·AI 보호 정책", "버전·멱등 키·허용 상태 전이 / Fixed·Dynamic·Cost·입출력 Guard·Privacy"),
]
for y, h, title, body in stages:
    box(48, y, 784, h, title, body, BLUE, 16)
for first, second in zip(stages, stages[1:]):
    arrow([(440, first[0]+first[1]), (440, second[0])])
for y, title, body in [
    (316, "401 / 403", "로그인 없음·만료 / 인증 후 역할·경로 권한 부족"),
    (474, "400 / 404 / 403", "잘못된 입력 / 없는 대상 / 객체 접근 권한 부족"),
    (632, "409 또는 정책 안전 응답", "키·버전·상태 충돌 / blocked·fallback·사유 코드"),
]:
    box(900, y, 332, 110, title, body, RED, 16)
    arrow([(832, y+55), (900, y+55)], RED)
box(48, 780, 1184, 80, "관측과 응답", "HTTP 상태 + reasonCode / detailCode + traceId + 저장·후속 호출 여부를 함께 확인합니다.", CYAN, 15)
arrow([(440, 742), (440, 780)])
graph_note("보안 실패 경계", ["경로·메서드 → 공개/JWT/ADMIN Basic → DTO·객체 권한 → 업무·AI 정책 → 응답·관측", "정책 차단은 HTTP 상태 하나로 판정하지 않습니다."])
imported("05", [7], "차단 사유·세부 사유·보호 결과의 구분을 보존합니다.")
imported("06", [14, 15, 16], "입력 6항목·미니 온톨로지·동일 사건 안내 예시는 고유한 제품 설명입니다.")

begin("07", "전체 시스템 Flow · 역할 → 서버 → 데이터·공급자", "사용자 역할, 공통 보안 경계, 업무 처리와 외부 호출·관측을 한 장으로 연결합니다.", "README / SecurityConfig / Controller·Service / JPA / Actuator")
for x, title, body in [
    (48, "USER · 화면01~05", "로그인·선택형 접수·안내·검색"),
    (462, "LAWYER · 화면06~08", "배정 문의·자료·상태·내부 메모"),
    (877, "ADMIN · 화면09~13", "배정·마케팅·보안·Incident·지표"),
]:
    box(x, 184, 355, 106, title, body, BLUE, 17)
    arrow([(x+177, 290), (x+177, 364)])
box(48, 364, 1184, 108, "Spring Security → Controller → DTO",
    "경로·메서드별 공개/JWT/ADMIN 정책 → 입력 검증 → 서비스의 소유·배정 권한\nActuator는 별도 ADMIN Basic 체인으로 분리합니다.", CYAN, 17)
arrow([(640, 472), (640, 535)])
box(48, 535, 1184, 111, "Service · 업무 처리와 AI 보호",
    "접수·검색·검토 / 트랜잭션·멱등성·버전 / Fixed·Dynamic·요청 예산 사전 확인·단계별 예약·정산·Privacy\n응답은 DTO·사유 코드·traceId로 조합하고 화면은 필요한 조회를 별도로 실행합니다.", GREEN, 17)
for x, title, body, color in [
    (48, "JPA · 업무·감사 기록", "문의·자료·검색 조건/결과\n메모·행동 이벤트·위험 이력·Incident", GREEN),
    (462, "외부 공급자 · 설정별", "LLM / 입출력 Guard / 외부 검색\n공급자 오류·시간 초과·부분 복구", GOLD),
    (877, "관측 · 운영 Read Model", "업무 DB 집계 + MeterRegistry\nPrometheus → 시계열 → 운영자 화면", CYAN),
]:
    arrow([(x+177, 646), (x+177, 734)])
    box(x, 734, 355, 122, title, body, color, 17)
graph_note("전체 시스템", ["USER/LAWYER/ADMIN → Spring Security → Controller·DTO → Service·정책", "Service → JPA / 외부 공급자 / 지표·Read Model", "RAG·Vector DB·Python Action의 추가 확장은 마지막 회고 장의 계획과 구분합니다."])

begin("07", "핵심 기능 Flow · 고객의 시작에서 운영까지", "화살표는 사용자 여정입니다. C03·C06·P03은 자동 연쇄 실행이 아니라 각각 별도 요청입니다.", "README / Screen-API-Controller 매핑")
journey = [
    ("a", 48, 184, "C02 · 사건 유형 확인", "선택형 입력·규칙 분류\n법률 Reference R01~R06", BLUE),
    ("b", 472, 184, "C03 · 문의 접수", "문의·자료·검색 조건 저장\nintakeId·conditionId 생성", BLUE),
    ("c", 896, 184, "C04·C05 · 간단 안내", "규칙·템플릿 안내·준비 자료\n초기 안내는 LLM 미호출", BLUE),
    ("d", 896, 413, "C06 · 검색 요청 준비", "저장 조건을 연결\nsearchRequestId 생성", GREEN),
    ("e", 472, 413, "P03 · 실행", "검색문 → 외부 검색\n결과 저장 → 관련성 설명", GREEN),
    ("f", 48, 413, "C07·P02 · 결과·이력", "결과·출처·실행 상태 조회\nPARTIAL은 설명만 재시도", GREEN),
    ("g", 48, 642, "C05·C09 · 변호사 검토", "배정 문의·첨부·사건 정보\nC21 배정·회수는 ADMIN", CYAN),
    ("h", 472, 642, "C10·C11 · 상태·메모", "버전·전이·재전송 확인\n내부 메모는 USER에서 제외", CYAN),
    ("i", 896, 642, "C12~C20 · 운영·보안", "마케팅·비용·위험 이력\n관측 → 근거 확인 → 처리", CYAN),
]
for _, x, y, title, body, color in journey:
    box(x, y, 336, 134, title, body, color, 17)
for points in [
    [(384, 251), (472, 251)], [(808, 251), (896, 251)],
    [(1064, 318), (1064, 413)], [(896, 480), (808, 480)],
    [(472, 480), (384, 480)], [(216, 547), (216, 642)],
    [(384, 709), (472, 709)], [(808, 709), (896, 709)],
]:
    arrow(points)
text("공통 보호: JWT·객체 권한 / Fixed·Dynamic·Cost / 출력 보호 / Metric·위험 이력", 50, 825, 1180, 18, GOLD, True)
graph_note("고객·변호사·운영 여정", [item[3] + ": " + item[4].replace("\n", " · ") for item in journey])

begin("08", "핵심 Flow ① · 문의 저장 → 검색 실행 → 결과", "ID 전달과 비동기 실행·수동 저장·부분 복구를 구분합니다.", "LegalIntakeRecordService / SearchExecutionService·Store / SearchLlmService")
for y, h, title, body in [
    (181, 104, "C03 · 문의 등록", "LegalIntakeRecordController.create → 트랜잭션 저장\nintakeId·자료·LawyerSearchCondition 저장"),
    (310, 104, "C06 · 별도 검색 요청 생성", "문의 접근·최신 조건 확인 → AiSearchRequest / PREVIEW_READY\n반환 id = searchRequestId"),
    (439, 110, "P03 · 실행 요청 → 202", "SearchExecutionController → execute → 실행권 claim·작업 큐\n후속 run: 검색문 → 외부 검색 → 결과 저장 → 결과별 설명"),
    (590, 110, "저장 결과·실행 상태", "SearchResultSnapshot은 같은 searchRequestId로 연결\nCOMPLETED / NO_RESULTS / PARTIAL / FAILED"),
    (742, 110, "C07 상세 · P02 문의별 이력", "화면이 별도로 재조회 → 조건·정렬된 결과·출처·실행 상태 표시\n문의 이력에는 intakeId, 요청 상세·실행·결과에는 searchRequestId"),
]:
    box(48, y, 784, h, title, body, BLUE if y < 439 else GREEN, 16 if y < 439 else 17)
for a, b in [(285,310),(414,439),(549,590),(700,742)]:
    arrow([(440,a),(440,b)])
box(900, 181, 332, 110, "공통 실패 경계", "인증·소유/배정·대상 연결\n401 / 403 / 404", RED, 17)
arrow([(832,228),(900,228)],RED)
box(900, 370, 332, 150, "C08 · 수동 결과 저장", "별도 POST로 보낸 결과만 저장\n실행 중 수동 추가는 409\nP03 내부 저장과 구분", GOLD, 17)
arrow([(900,445),(866,445),(866,645),(832,645)],GOLD)
box(900, 625, 332, 200, "PARTIAL → 설명만 복구", "retryExplanation:true\n기존 결과 ID·출처를 유지\n미완료 설명만 재생성\n외부 검색은 반복하지 않음", CYAN, 17)
arrow([(832,672),(900,672)],CYAN)
graph_note("문의·검색", ["C03 저장 → C06 별도 요청 → P03 별도 실행·202 → run·결과 저장 → C07/P02 별도 조회", "C08 수동 저장과 P03 내부 저장은 분리합니다.", "PARTIAL은 저장 결과를 유지하며 미완료 설명만 재시도합니다."])

begin("08", "핵심 Flow ② · 상태·메모 → 안전한 재조회", "같은 문의 행 잠금 안에서 키·작성자·버전·허용 전이를 판정합니다.", "LegalIntakeRecordService / IntakeAccessService / PrivacyOutputGuardService")
box(48, 180, 1184, 87, "C05·C09에서 편집 대상 확인", "실제 intakeId·현재 version → C10 상태 또는 C11 상담 메모를 별도 요청", BLUE, 18)
arrow([(640,267),(640,300)])
diamond(475,300,330,118,"수정 권한 확인","배정 LAWYER / ADMIN")
box(940,312,292,102,"권한 없음 → 403","화면 버튼과 별개로 서버에서 차단",RED,16)
arrow([(805,359),(940,359)],RED)
arrow([(550,391),(550,432),(323,432),(323,468)])
arrow([(730,391),(730,432),(957,432),(957,468)])
box(48,468,550,132,"C10 · 상태 변경","같은 상태는 변경 없음\n다른 상태는 expectedVersion → canChangeTo → 변경·flush",BLUE,17)
box(682,468,550,132,"C11 · 상담 메모","작성자·키·내용 해시로 재전송 판정\n새 요청은 버전 확인 → 메모 저장·touch·flush → 마스킹",GREEN,17)
arrow([(323,600),(323,644)])
arrow([(957,600),(957,644)])
box(48,644,550,149,"상태 결과","200 · 동일 상태: changed=false / 버전 유지\n200 · 허용 변경: 최신 version 반환\n409 · 오래된 버전·금지된 역행",BLUE,18)
box(682,644,550,149,"메모 결과","201 · 새 메모 또는 기존 메모 재전송\nmemoId·intakeVersion·replay 헤더로 구분\n409 · 같은 키의 다른 내용·버전 충돌",GREEN,18)
c.setFillColor(HexColor(PANEL));c.setStrokeColor(HexColor(CYAN))
c.roundRect(48,H-866,1184,40,8,fill=1,stroke=1)
text("화면이 C05·C09를 별도 재조회합니다. USER 응답에는 내부 상담 메모를 포함하지 않습니다.",65,834,1150,15,CYAN)
arrow([(323,793),(323,826)])
arrow([(957,793),(957,826)])
graph_note("상태·메모", ["C05/C09 → 수정 권한 → C10 / C11의 독립 트랜잭션 → 결과 → 별도 재조회", "expectedVersion은 호환 정책상 선택값이며, 제공해야 오래된 편집을 검출합니다.", "기존 메모 재전송 판별은 버전 검사보다 먼저 수행합니다."])

begin("08", "핵심 Flow ③ · AI·SSE의 비용·출력 생명주기", "입력 검사 뒤 첫 유료 단계 전에 전체 예상 비용을 확인하고, 실제 호출은 단계별로 예약·정산합니다.", "AiService / AiStreamingService / AiCostControlService / CostedGuardrailService")
box(48,194,345,118,"JWT·DTO → 무료 검사","Dynamic → Fixed → 입력 길이\n차단 시 후속 LLM 호출 중단",BLUE,17)
diamond(470,172,320,160,"전체 요청 예산 확인","precheckRequestBudget\n판정만 수행 · 예약 아님")
box(885,194,347,118,"입력 Guard","reserve → 검사 → 정산\n불허면 답변 생성 중단",GREEN,17)
arrow([(393,253),(470,253)])
arrow([(790,253),(885,253)],label="허용",lx=810,ly=222,lw=70)
box(885,451,347,118,"답변 생성","답변 비용 예약 → LLM\nusage 확인·실사용/추정 정산",GREEN,17)
arrow([(1058,312),(1058,451)])
box(470,451,320,118,"출력 Guard","예약 → 전체 출력 검사\n이미 생성한 비용은 유지",GREEN,17)
arrow([(885,510),(790,510)])
box(48,451,345,118,"Privacy 출력 보호","지원 패턴을 마스킹\nSSE도 전체 검사 뒤 전송",BLUE,17)
arrow([(470,510),(393,510)])
box(48,707,345,119,"정상 응답","S01 JSON / S02 token·done\ntraceId·사유·usage 정보",BLUE,17)
arrow([(220,569),(220,707)])
box(470,707,320,119,"지표·위험·비용 관측","stage·reason·토큰·예약 잔여\nC16 / C17 / 시계열",CYAN,17)
arrow([(393,766),(470,766)])
box(885,707,347,133,"차단·오류·취소","blocked / error / fallback\n실행된 비용 정산·미호출 예약 해제",RED,17)
arrow([(630,332),(630,366),(420,366),(420,628),(885,628),(885,731)],RED,label="예산 불가",lx=650,ly=340,lw=150)
arrow([(220,312),(220,366),(420,366),(420,628),(885,628),(885,731)],RED,label="입력·고정·반복 위험 차단",lx=50,ly=338,lw=330)
arrow([(1232,253),(1250,253),(1250,766),(1232,766)],RED,label="입력 Guard 불허",lx=1080,ly=338,lw=155)
arrow([(1058,569),(1058,707)],RED,label="공급자 오류",lx=1080,ly=620,lw=140)
arrow([(630,569),(630,628),(885,628),(885,731)],RED,label="출력 차단·오류",lx=660,ly=597,lw=210)
arrow([(885,766),(790,766)],RED)
text("SSE 취소·오류는 공급자 취소와 중복 종결 방지를 처리합니다. usage 미확인·호출됨은 추정 정산하며, 추정값을 실제 청구액으로 보지 않습니다.",50,842,1180,13,MUTED)
graph_note("AI·SSE·비용", ["JWT·DTO → Dynamic·Fixed·길이 → 요청 전체 예산 사전 확인 → 입력 Guard → 답변 생성 → 출력 Guard → Privacy → 응답", "각 유료 단계 reserve·usage/추정 정산. 출력 차단에도 이미 생성한 답변 비용은 유지합니다.", "SSE는 전체 출력 검사·마스킹 후 token/done을 전달합니다."])

begin("09", "전체 API 지도 · 38개 + 보조 Actuator", "번호 API는 C21 + R6 + S5 + P6입니다. 아래 책임 그룹과 다음 상세 표를 함께 읽습니다.", "SCREEN_API_CONTROLLER_MAPPING / API_STATUS_SPEC / SecurityConfig")
api_groups = [
    (48,184,"접속·공개 안내","C01 로그인 · C02 규칙 분류\nR01~R06 법률 Reference · P06 행동 보고\nP01 현재 사용자는 JWT 필요",BLUE),
    (658,184,"문의·검색·파일","C03~C09 · P02~P05\nJWT + 문의 소유/배정/ADMIN\n생성 ID → 후속 요청 ID 전달",GREEN),
    (48,410,"변호사 업무·배정","C10 상태 · C11 메모: 배정 LAWYER/ADMIN\nC21 배정·회수: ADMIN\n버전·키·허용 전이를 함께 확인",CYAN),
    (658,410,"운영·위험·Incident","C12~C15 · C17~C20: ADMIN JWT\nS05 위험 분석 쓰기도 ADMIN JWT\n미등록 /api 경로는 기본 차단",CYAN),
    (48,636,"AI·Guardrail","S01 동기 / S02 SSE / S03 입력 / S04 출력\nJWT → 정책·비용·출력 보호\n요청·단계·실제 청구액을 구분",GOLD),
    (658,636,"관측 경로","C16 /actuator/prometheus\n보조 /actuator/guardtimeseries\n업무 JWT와 별도 ADMIN Basic",GOLD),
]
for x,y,title,body,color in api_groups:
    box(x,y,574,178,title,body,color,19)
graph_note("API 전체 지도", [title+": "+body.replace("\n"," · ") for _,_,title,body,_ in api_groups])

# Extract the original editable mapping without executing the legacy PDF builder.
api = {}
for tab in tables(ROOT/"docs/SCREEN_API_CONTROLLER_MAPPING.md"):
    for row in tab[1:]:
        if len(row) != 6:
            continue
        match = re.search(r"\*\*([CRSP]\d{2})\*\*", row[1])
        if match and match[1] not in api:
            api[match[1]] = [clean(v) for v in row]
legacy = ast.parse((ROOT/"scripts/Build-TeamProjectReport.py").read_text(encoding="utf-8"))
extra = next(ast.literal_eval(node.value) for node in legacy.body if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id=="extra" for t in node.targets))
for code, screen, name, url, fields, auth, entry in extra:
    api[code] = [screen,code+" "+name,url,fields,auth,entry]
api["S05"][4] = "ADMIN JWT"
assert len(api)==38, sorted(api)
api_rows=[]
for prefix in "CRSP":
    for code in sorted((key for key in api if key.startswith(prefix)),key=lambda value:int(value[1:])):
        screen,name,url,fields,auth,entry=api[code]
        api_rows.append([screen,name,url+"\n"+fields,auth,entry])
api_rows.append(["13","보조 Actuator","GET /actuator/guardtimeseries\nParams/Body 없음","ADMIN Basic","GuardTimeSeriesEndpoint.read → PrometheusTimeSeriesService.read"])
table("09","전체 Screen-API-Controller 매핑","S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.",
      ["화면","API·기능","HTTP·입력 계약","인증·대상","Controller → Service"],api_rows,[80,180,410,175,339],12.5,"main / 64f7f62 · 현행 매핑·SecurityConfig")
imported("09",[35],"ID 전달 표는 상세 계약의 보조 자료로 유지합니다.")
imported("10",[8],"역할별 보드 설명을 한 번만 제시합니다.")
table("10","마케팅 집계 · 숫자의 의미를 먼저 구분","가상 여정의 API·DB 검증과 상담 후 합성 전환 분석을 혼합하지 않습니다.",
      ["대상","관측한 결과","해석 기준"],[
          ["방문·작성·안내","P06 시드로 VISIT 40 / INTAKE_STARTED 28 / SUMMARY_VIEWED 16","React 자동 수집 미연결. 실제 고객 성과가 아니라 집계 로직 검증"],
          ["문의·검색","신규 문의20 / 검색 요청13회 / 검색한 문의10건","요청 횟수를 신규 고객 수로 합산하지 않음"],
          ["현재 상담 준비","6건 READY → 1건 CLOSED → 현재5건","현재 상태이며 누적 상담·계약 건수와 다름"],
          ["분포·무결성","신규20건 분포 대조 / 조작·권한 위반10건 거절 / 기존20/20 통과","중복 이벤트 제외·키/내용 충돌 검증"],
          ["상담 후 시연 분석","가상40→24→12→4 및 수임·이탈 사유","프런트 합성 자료. API 시드40→28→20→16과 별도"],
      ],[190,450,544],17,"원본90~93쪽 / MARKETING_DEMO_REVIEW / MARKETING_CONVERSION_GAP_REVIEW")
imported("10",[90],"가상 여정과 실제 API 집계의 대표 캡처 한 장으로 통합합니다.")
imported("11",[9],"실시간 관측·근거 확인·운영자 대응의 책임을 보존합니다.")

table("12","현재 구현·검증 상태","main 반영과 과거 실공급자 실험, 앞으로의 운영 배포를 서로 다른 근거로 관리합니다.",
      ["영역","현재 기준","검증·남은 범위"],[
          ["코드·문서 기준","main / 64f7f62 · 베타 자료 + 리팩터링 + 승인 README·회고","beta/2026-10-23은 이전 버전으로 보존"],
          ["회귀 검사","백엔드35개 suite · 276개 테스트 · 실패0 / 프런트 운영자·문의·Q02·실험 검수 도구 검사 및 빌드 통과","직전 게시 작업에서 실행한 결과. 이번 PDF 편집에서 새 유료 호출·서버 실험 없음"],
          ["오늘의 코드 보완","운영 쓰기 경로 ADMIN · 전체 요청 예산 사전 확인 · 검색/Incident 연결 · LAN UUID 대체 생성","회고의 코드 버그와 실행 환경 문제를 구분"],
          ["제품·데이터","화면01~13 · 소유/배정·메모 비공개·키·해시·버전·전이","시연 서버의 메모리 DB·예산 장부와 영속 운영 환경 구분"],
          ["실제 LLM 기록","과거 OpenAI 호출과 usage·예약 종료·공급자 화면 대조","모의 Guard 장부를 실공급자 청구액에 합산하지 않음"],
          ["운영·확장","업무 DB 집계·지표·위험 이력·Incident","HTTPS·운영 계정·영속 예산·실측 여정 / Python Action·RAG는 계획"],
      ],[185,500,499],16,"main 이력 / Gradle XML / 원본 실험 기록 / README")
table("12","수행 방법·일정 · 14일 흐름","AI 초안 → 정상·실패·차단 검증 → 정책·코드 매핑 → 수정·재검증 → 문서·시연으로 진행합니다.",
      ["단계","핵심 작업","확인할 산출물"],[
          ["Day1~2","시장 문제·MVP·화면·DB·API 설계","Ground Rule·화면 정의·ERD·API 매핑"],
          ["Day2~5","Backend Draft·보안·비용·DB 연결","Controller·Service·Entity·reasonCode·실패 경계"],
          ["Day6","Mapping Check + Refactoring","화면→API→코드→응답/ID/DB 대조"],
          ["Day7","DDD·Event Storming","Actor→Command→Event→Policy→Code / Top–Middle–Bottom"],
          ["Day8~10","React·Dashboard·LLM Search Routing","화면/API·검색문·외부 검색·결과·설명"],
          ["Day11~13","PowerShell 통합 검증·Refactoring·시연","정상/실패/차단 요청·로그·지표·영상·회고"],
          ["Day14","발표·제출 품질 강화","README·흐름도·캡처·보고서·발표 문장 최종 정리"],
      ],[170,450,564],17,"GROUND_RULE / PROJECT_14_DAY_SCHEDULE_2026-10-26")
table("12","시연 영상 · 핵심 흐름과 검증 구간","기존9분·30장면 영상의 핵심 구간을 한 장으로 통합합니다. 세부 장면별 대본은 별도 편집 원본에 있습니다.",
      ["구간","보여주는 흐름","근거·구분"],[
          ["00:00–01:40","선택형 접수 → 안내·검색 → 변호사 검토·상태·메모","규칙 안내·모의 검색·역할별 업무"],
          ["01:40–02:25","세션·일일·월 예산 차단","모의 공급자 예산 실험"],
          ["02:25–03:20","실제 OpenAI 응답·토큰·정산·출력 상한","실제 LLM 기록 / 공급자 사용량 대조"],
          ["03:20–03:45","입력400·저장 충돌409","입력 보존·오래된 편집 처리"],
          ["03:45–07:45","인증·Fixed·Dynamic·Privacy·객체 권한·중복·상태·부분 복구","기존 모의 공급자·DB·후속 중단 검증"],
          ["07:45–08:45","가상 고객 여정의 실제 API 집계·중복 방지","마케팅 기존20/20 기록 / 실제 매출 성과와 구분"],
          ["08:45–09:00","모바일 입력·마무리","기존 작은 화면 뷰포트 / 실제 기기 전체 검증과 구분"],
      ],[180,580,424],16,"원본94~96쪽 / 09M_DEMO_FLOW_TIMESTAMP_KEYWORD / 09M_PRESENTATION_TIMED_SCRIPT")
begin("13","Core Message · 이번 설계의 의미","모델 연결을 넘어 정책·비용·운영 결과를 설명하고 검증하는 구조를 만들었습니다.","README / Ground Rule")
text("고객은 선택하고, 챗봇은 정리하며,\n보안은 LLM 호출 전에 통제하고,\n운영자는 지표와 근거로 관찰한다.",65,217,1150,37,CYAN,True)
box(56,455,1168,153,"정책에서 코드와 검증으로","Fixed·Dynamic·Cost·Privacy를 역할별 API와 연결하고 reasonCode·Metric·조회 기록으로 처리 결과를 확인합니다.",GREEN,21)
box(56,642,1168,166,"구현에서 운영 설명으로","정상 응답뿐 아니라 차단·중복·버전 충돌·부분 실패·사용량 정산까지 다룹니다.\n자동화의 결과도 실제 코드·저장·지표와 대조해 설명하는 것을 기준으로 삼습니다.",BLUE,21)

# Technical appendices hold unique details instead of repeating the narrative.
imported("A",[26,27,28,29],"전체 관계·실제 필드·제약을 보존하고, 저해상도 전체 ERD 중복 사진은 제거했습니다.")
imported("B",list(range(40,54)),"화면01~13과 모바일의 서로 다른 실제 캡처를 모두 유지합니다. 중복 화면 목록표는 제거했습니다.")
ddd=tables(ROOT/"docs/BACKEND_DDD_EVENT_STORMING.md")
table("C","DDD · 책임 경계와 Read Model","단일 Spring Boot 내부의 책임 경계 후보이며 마이크로서비스 분리 완료를 뜻하지 않습니다.",
      ddd[0][0],ddd[0][1:],[230,385,569],15,"BACKEND_DDD_EVENT_STORMING")
ddd_rows=[]
for tab in (ddd[4],ddd[5]):
    for row in tab[1:]:
        actor,command,event,policy,aggregate,read,entry=[clean(v) for v in row]
        if "S05" in entry or "AgentRiskDecision" in entry:
            actor="ADMIN JWT"
            policy="ADMIN 인증·권한 확인. "+re.sub(r"현재.*?공개[.· ]*", "", policy)
        ddd_rows.append([actor,command+"\n→ "+event,policy,aggregate+"\nRead: "+read,entry])
for row in ddd[7][1:]:
    command,event,policy,store,entry=[clean(v) for v in row]
    ddd_rows.append(["공통 정책·업무",command+"\n→ "+event,policy,store,entry])
table("C","Actor → Command → Event → Policy → Code","Event는 학습용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.",
      ["Actor","Command → Event","Policy","저장 / Read Model","코드 진입점"],ddd_rows,[130,250,280,245,279],11.7,"DDD 원본 / main 권한 정책으로 S05 보정")
imported("C",[60],"설계 용어와 실제 구현 경계의 고유 설명을 보존합니다.")

table("D","검증 결과 · 정상·실패·차단·저장","아래는 원본 보고서의 과거 실험 결과입니다. 현재 main 회귀 결과는 본문의 수행결과와 구분합니다.",
      ["검증 축","원본 관측","해석 기준"],[
          ["인증·입력","G1·G2: JWT 없으면401 / 입력 검증400","후속 생성·저장 중단 여부를 함께 확인"],
          ["Fixed·Dynamic","G3: 비밀 요청 차단 / G5·A3: 같은 세션 제한·정상 문장으로 바꿔도 유지","한 실험을 모든 공격 유형 실측으로 확대하지 않음"],
          ["Privacy","G7: 답변·접수·메모에서 검사한 원문 패턴0","지원 패턴의 검사 결과이며 모든 개인정보 보호 보장 아님"],
          ["객체 권한·중복","다른 USER403·배정 회수403 / 접수8→고유ID1·메모6→1","HTTP201 횟수와 실제 저장 건수를 구분"],
          ["상태·부분 복구","금지 역행409·동일 상태 no-op / PARTIAL 결과ID 유지·설명만 재시도","문의·Incident 규칙과 검색·설명 호출을 구분"],
          ["모의 오류·비용","예산 사전 차단 LLM0 / 모의500 뒤 다음 정상 요청 처리","초기 파서의 estimated+0을 실제 비용0 증거로 사용하지 않음"],
      ],[185,490,509],16,"원본61~71쪽 / 대표 캡처는 뒤에 유지")
evidence=[]
for n in [73,74,76,77,78,79,80,82,83,86,87,88]:
    raw=original_rows[n-1]["text"]
    condition=re.search(r"실험 조건\n(.*?)\n무엇을 확인하나",raw,re.S)
    observed=re.search(r"관측 결과\n(.*?)\n실험 증거",raw,re.S)
    cost=re.search(r"비용·OpenAI 대조\n(.*?)\n2026-10-26",raw,re.S)
    assert condition and observed and cost,n
    title=next(line.strip() for line in raw.splitlines() if re.match(r"^(?:A\d|B\d|C\d|V\d)",line.strip()))
    evidence.append([title,condition[1].replace("\n"," "),observed[1].replace("\n"," "),cost[1].replace("\n"," ")])
table("D","Guard·비용·오류 실험 결과 대조","실험별 조건·관측·비용 해석을 한 표로 통합했습니다. 금액은 과거 기록이며 현재 요금 견적이 아닙니다.",
      ["실험","조건","관측 결과","비용·공급자 대조"],evidence,[190,290,350,354],13,"원본73~88쪽 / 실험별 원문")
imported("D",[65,67,69,73,79,82,83,88],"Privacy·동시 재전송·부분 복구·Dynamic·예산 개선·실제 usage·품질·timeout의 대표 원본 캡처입니다. 동일 목적의 반복 캡처는 결과 표로 통합했습니다.")

# Full approved retrospective, followed by the expansion plan from the README.
end()
retro=ROOT/"docs/presentation-pdfs/DEMO_DEPLOYMENT_RETROSPECTIVE_DARK_TABLET.pdf"
for n in range(len(PdfReader(retro).pages)):
    parts.append(dict(file=str(retro),page=n,section="15",title=[
        "오늘의 회고", "오늘 겪은 오류11건", "시연 전날 준비", "기동 순서1", "기동 순서2",
        "외부 PC 접속", "시연 직전 스모크 테스트", "종료·정리", "운영 배포 보완"][n],kind="retrospective"))
readme=(ROOT/"README.md").read_text(encoding="utf-8")
future_body=readme.split("### 8. 앞으로의 확장 계획",1)[1]
future_rows=[]
for line in future_body.splitlines():
    if line.startswith("|") and not line.startswith("|---") and not line.startswith("| 순서"):
        values=[clean(v.strip()) for v in line.strip()[1:-1].split("|")]
        if len(values)==3:future_rows.append(values)
assert len(future_rows)==8
table("15","앞으로의 확장 계획","현재 구현과 향후 학습·개발 계획을 구분합니다. 12월 실무형 MVP 확장 목표를 유지합니다.",
      ["순서","확장·학습 방향","구현·검증 기준"],future_rows,[75,300,809],16,"승인된 README / 회고·확장 계획")
text("이해 강화 → 운영 기반 정리 → 작은 Python Action → 공식 자료 RAG → 상담·후속 안내·분석 확장",50,804,1180,17,GREEN,True)
text("새 기능에도 정상·실패·차단 회귀 검증과 코드·DB·지표 대조를 유지합니다.",50,842,1180,15,MUTED)
end()
c.save()

# Assemble without flattening: retain selectable text and native vectors.
readers={str(ORIGINAL):PdfReader(ORIGINAL),str(AUTHORED):PdfReader(AUTHORED),str(retro):PdfReader(retro)}
writer=PdfWriter()
ranges={}
for index,part in enumerate(parts):
    key=part["section"]
    ranges.setdefault(key,[index+1,index+1])[1]=index+1
    src_page=readers[part["file"]].pages[part["page"]]
    page=writer.add_blank_page(W,H)
    scale=min(W/float(src_page.mediabox.width),H/float(src_page.mediabox.height))
    page.merge_transformed_page(src_page,Transformation().scale(scale))
    if page.get("/Annots"):
        del page["/Annots"]

# Core Message and design meaning share one page to avoid a duplicate conclusion.
ranges["14"]=list(ranges["13"])
total=len(parts)
for index,(part,page) in enumerate(zip(parts,writer.pages)):
    buf=BytesIO()
    overlay=canvas.Canvas(buf,pagesize=(W,H))
    overlay.setFillColor(HexColor(BG));overlay.rect(0,0,W,59,fill=1,stroke=0)
    overlay.setStrokeColor(HexColor(LINE));overlay.line(48,54,1232,54)
    overlay.setFont("KR",10);overlay.setFillColor(HexColor(MUTED))
    label=NAMES.get(part["section"],"팀 프로젝트 결과보고서")
    overlay.drawString(48,30,"2026-10-26  |  "+label)
    if index>1:
        overlay.setFillColor(HexColor(CYAN))
        overlay.drawString(W-285,30,"목차로")
        overlay.setFillColor(HexColor(MUTED))
    overlay.drawRightString(1232,30,f"{index+1:03d} / {total:03d}")
    overlay.save();buf.seek(0)
    page.merge_page(PdfReader(buf).pages[0])
    if index>1:
        writer.add_annotation(index,Link(rect=(W-300,8,W-200,48),target_page_index=1))

toc=BytesIO();tc=canvas.Canvas(toc,pagesize=(W,H))
main=[(k,v) for k,v in SECTIONS if k.isdigit()]
appendices=[(k,v) for k,v in SECTIONS if not k.isdigit()]
for i,(key,label) in enumerate(main):
    y=H-193-i*38
    tc.setFont("KB" if key=="15" else "KR",15)
    tc.setFillColor(HexColor(CYAN if key=="15" else TEXT))
    tc.drawString(58,y,key+"  "+label)
    tc.drawRightString(793,y,str(ranges[key][0]))
    writer.add_annotation(1,Link(rect=(48,y-8,805,y+24),target_page_index=ranges[key][0]-1))
tc.setFillColor(HexColor(PANEL));tc.roundRect(841,210,391,560,12,fill=1,stroke=0)
tc.setFillColor(HexColor(CYAN));tc.setFont("KB",18);tc.drawString(860,730,"상세 근거·편집 기준")
for i,(key,label) in enumerate(appendices):
    y=674-i*81
    tc.setFillColor(HexColor(TEXT));tc.setFont("KR",13)
    tc.drawString(860,y,label);tc.drawString(860,y-25,f"{ranges[key][0]}~{ranges[key][1]}쪽")
    writer.add_annotation(1,Link(rect=(850,y-36,1221,y+24),target_page_index=ranges[key][0]-1))
tc.setFont("KR",12);tc.setFillColor(HexColor(MUTED))
for i,line in enumerate(["제품 소개·보드·실험 안내의 반복은 통합","원본의 고유 화면·필드·관측값은 보존","흐름도는 현행 main의 정책·ID로 보완","마지막 장에 오늘의 회고·확장 계획 배치"]):
    tc.drawString(860,290-i*24,line)
tc.save();toc.seek(0);writer.pages[1].merge_page(PdfReader(toc).pages[0])
for key,label in SECTIONS:
    writer.add_outline_item(key+" "+label,ranges[key][0]-1)
writer.add_metadata({"/Title":"Legal AI Intake · 팀 프로젝트 결과보고서 · 전체 Flow·회고 통합본",
                     "/Author":"Legal AI Intake Project","/Subject":"README 순서·중복 제거·전체 및 핵심 흐름도·시연 배포 회고","/Keywords":"Legal Intake, Flowchart, API, Security, Cost, Retrospective"})
with OUT.open("wb") as f:writer.write(f)
check=PdfReader(OUT)
assert len(check.pages)==total
assert len(check.outline)==len(SECTIONS)
assert len(check.pages[1].get("/Annots",[]))==len(SECTIONS)
assert all(page.extract_text().strip() for page in check.pages)
text_all="\n".join(page.extract_text() for page in check.pages)
for value in ["precheckRequestBudget","PARTIAL","S05","ADMIN JWT","crypto.randomUUID","11","12월","RAG","Antigravity"]:
    assert value in text_all,value
kept={p["originalPage"] for p in parts if "originalPage" in p}
dropped=[]
for row in original_rows:
    if row["page"] not in kept:
        dropped.append(dict(page=row["page"],title=row["title"],
            reason="중복 설명·목차·상세 캡처를 통합 표·새 Flow·현행 정책·대표 증거로 대체. 원본 PDF는 변경하지 않음."))
manifest=dict(original=str(ORIGINAL),originalPages=97,originalSha256=sha256(ORIGINAL.read_bytes()).hexdigest(),
              output=str(OUT),pages=total,sections=[dict(key=k,title=v,start=ranges[k][0],end=ranges[k][1]) for k,v in SECTIONS],
              pagesDetail=parts,removedOrReplaced=dropped,flowcharts=graphs,layoutBounds=bounds,
              numberedApiCount=len(api),auxiliaryActuatorCount=1,preservedScreens=list(range(1,14)),
              newProviderCalls=0,newServerTests=0,codeReference="main / 64f7f62")
(QA/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
STRUCTURE.write_text("# 최종 보고서 · 편집·출처 구조\n\n"+
    f"원본97쪽 → 개정{total}쪽. README 순서·실제 화살표 Flow·오늘 회고·확장 계획 통합.\n\n"+
    "\n".join(f"- {k} {v}: {ranges[k][0]}~{ranges[k][1]}쪽" for k,v in SECTIONS)+
    "\n\n# 편집 가능한 본문·표·흐름 단계\n"+"".join(notes),encoding="utf-8")
print(json.dumps(dict(pdf=str(OUT),pages=total,originalPages=97,authoredPages=authored_page,
                      diagrams=len(graphs),apiCount=len(api),contentsLinks=len(SECTIONS),bytes=OUT.stat().st_size),ensure_ascii=False))
