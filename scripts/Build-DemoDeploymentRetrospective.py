"""Build a Korean dark-mode demo/deployment retrospective and its editable source."""
from pathlib import Path
from xml.sax.saxutils import escape
import json
import re

from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output/pdf/DEMO_DEPLOYMENT_RETROSPECTIVE_DARK_TABLET.pdf"
REPO_PDF = ROOT / "docs/presentation-pdfs/DEMO_DEPLOYMENT_RETROSPECTIVE_DARK_TABLET.pdf"
SOURCE = ROOT / "docs/presentation-2026-10-25/14_DEMO_DEPLOYMENT_RETROSPECTIVE.md"
QA = ROOT / "tmp/pdfs/demo-deployment-retrospective"
DATE = "2026-10-26"
W, H = 1280, 960
BG, PANEL, CODE = "#0d1523", "#162338", "#09111d"
TEXT, MUTED, LINE = "#edf3fb", "#adbed3", "#30445e"
CYAN, BLUE, GOLD = "#6cddce", "#82b9ff", "#f0c572"
TICK = chr(96)

ERRORS = [
    ("스크립트 경로 오류", "관리자 창이 C:\\WINDOWS\\system32에서 시작", "먼저 cd로 작업 폴더 이동", "환경"),
    ("vite 명령 없음", "새 작업 폴더에 node_modules 없음", "npm install 먼저 실행", "환경"),
    ("npm run dev 실패", "저장소 루트에서 실행", "frontend\\legal-procedure-viewer에서 실행", "환경"),
    ("127.0.0.1:5176 안 열림", "Vite가 localhost / ::1에만 바인딩", "--host 0.0.0.0으로 실행", "환경"),
    ("옛 Previewer 화면", "주소를 /로 열음", "/intake.html 또는 /operator.html 사용", "환경"),
    ("외부검색 버튼 회색", "CSS가 항상 흐리게 표시 (코드 버그)", ":disabled일 때만 회색 - 수정 완료", "코드"),
    ("외부 PC 로그인 실패", "CORS 허용 출처에 LAN 주소 없음", "시작 전 CORS_ALLOWED_ORIGIN_PATTERNS 설정", "환경"),
    ("방화벽 규칙 거부", "일반 PowerShell에서 실행", "관리자 PowerShell에서 실행", "환경"),
    ("외부 PC 문의 접수 실패", "crypto.randomUUID는 HTTPS·localhost에서만 사용 가능 (코드 버그)", "대체 UUID 생성 - 수정 완료", "코드"),
    ("다운로드 파일 없음", "오늘 받은 파일을 확인하지 않음", "받은 파일 이름·날짜 확인", "환경"),
    ("비용 숫자 초기화", "메모리 예산 장부가 재시작 시 0으로 초기화", "실제 비용은 OpenAI 사용량 화면에서 확인", "환경"),
]

PREP = [
    dict(title="작업 폴더", body="cd로 프로젝트 작업 폴더로 이동합니다. system32에서 시작하지 않습니다."),
    dict(title="최신 코드", body="git status가 깨끗한지 확인합니다. 패치는 적용 전에 검사합니다.", code="git status\ngit apply --check <패치파일>"),
    dict(title="백엔드 JAR", body="백엔드 폴더에서 실행 파일을 빌드합니다.", code="cd backend\\guardrail-ready-policy-checker\n.\\gradlew.bat bootJar"),
    dict(title="프론트 의존성", body="프론트 폴더에서 먼저 의존성을 설치합니다.", code="cd frontend\\legal-procedure-viewer\nnpm install"),
    dict(title="키 파일", body=".local\\secrets\\openai-key.dpapi가 있어야 합니다. 없으면 Set-LocalOpenAiKey.ps1을 실행합니다."),
    dict(title="환경변수 충돌", body="Windows 환경변수에 OPENAI_API_KEY·LLM_API_KEY가 없어야 합니다. 있으면 시작 스크립트가 STOP으로 멈춥니다."),
    dict(title="포트", body="18090(Spring)·18181(모의)·18282(브리지)·5176(Vite)가 비어 있는지 확인합니다.", code="Get-NetTCPConnection -State Listen -LocalPort 18090,18181,18282,5176"),
    dict(title="서버 IP", body="ipconfig로 서버 PC의 IP를 확인합니다. 오늘 기록: 192.168.10.48. IP가 달라지면 뒤의 예시도 바꿉니다.", code="ipconfig"),
    dict(title="남은 크레딧", body="OpenAI 사용량 화면에서 남은 크레딧을 확인합니다."),
]

START = [
    dict(title="창 ① | 모의 provider", body="Guard용 무료 모의 provider입니다. 저장소 루트에서 실행하며 18181 포트를 사용합니다.",
         code="node frontend\\legal-procedure-viewer\\scripts\\p03-mock-providers.mjs",
         note="이 창을 켜 둔 상태에서 다음 창을 시작합니다."),
    dict(title="창 ② | 검색 브리지", body="실제 검색은 유료입니다. 저장소 루트에서 실행하고, 확인 질문에 y를 입력합니다. 18282 포트를 사용합니다.",
         code=".\\scripts\\Start-OpenAiSearchBridge.ps1 -MaxSearches 20",
         note="최대 검색 횟수는 20회입니다. 브리지가 실행된 뒤 Spring을 시작합니다."),
    dict(title="창 ③ | Spring", body="저장소 루트에서 CORS를 먼저 설정한 뒤 같은 창에서 서버를 시작합니다.",
         code="$env:CORS_ALLOWED_ORIGIN_PATTERNS = (\n"
              "  'http://127.0.0.1:[*],http://localhost:[*],' +\n"
              "  'http://192.168.10.48:[*]'\n"
              ")\n"
              f".\\scripts\\Start-LlmTestServer.ps1 -Mode RealLlm {TICK}\n"
              "  -SearchBridgePort 18282",
         note="READY: health=UP / lawyer search = OpenAI web search bridge 두 줄을 확인합니다."),
    dict(title="창 ④ | Vite", body="frontend\\legal-procedure-viewer 폴더에서 실행합니다.",
         code="cd frontend\\legal-procedure-viewer\n"
              "$env:API_PROXY_TARGET='http://127.0.0.1:18090'\n"
              "npm run dev -- --host 0.0.0.0 --port 5176",
         note="Network: http://192.168... 줄이 보이는지 확인합니다."),
]

LAN = [
    dict(title="방화벽", body="관리자 PowerShell에서 5176만 엽니다. 네트워크 프로필과 -Profile을 맞춥니다.",
         code="New-NetFirewallRule -DisplayName 'demo-5176' -Direction Inbound "
              f"{TICK}\n  -Protocol TCP -LocalPort 5176 -Action Allow -Profile Private\n"
              "Get-NetConnectionProfile"),
    dict(title="CORS와 바인딩", body="CORS에 서버 LAN 주소가 있고, Vite가 --host 0.0.0.0으로 실행 중이어야 합니다. IP 변경 시 CORS도 변경하고 Spring을 재시작합니다."),
    dict(title="시연 PC에서 접속", body="오늘 서버 IP 기준입니다. 시연 PC와 서버 PC가 같은 Wi-Fi 또는 스위치에 연결돼 있어야 합니다.",
         code="http://192.168.10.48:5176/intake.html\nhttp://192.168.10.48:5176/operator.html"),
    dict(title="로그인부터 쓰기까지", body="시연 PC에서 로그인과 문의 접수까지 끝까지 확인합니다. 읽기만 되고 쓰기는 실패할 수 있습니다. Ctrl+F5로 새 코드도 받습니다."),
]

SMOKE = [
    dict(title="서버 상태", body="서버 PC에서 health가 UP인지 확인합니다.", code="Invoke-RestMethod http://127.0.0.1:18090/actuator/health"),
    dict(title="사용자 흐름", body="시연 PC에서 사용자 로그인 → 선택형 질문 → 문의 접수까지 성공합니다."),
    dict(title="실제 LLM 안내", body="시연 PC에서 AI 간단 안내에 실제 LLM 답변이 나오는지 확인합니다."),
    dict(title="외부 검색 1회", body="외부검색 버튼이 진한 파란색인지 확인하고, 변호사 검색 1회 후 출처 링크가 나오는지 확인합니다. 최대 90초 기다립니다."),
    dict(title="정책 차단", body="탈옥 문장 1회가 BLOCKED_JAILBREAK으로 차단되는지 확인합니다."),
    dict(title="운영자 화면", body="관리자 로그인 후 /operator.html의 화면13 그래프와 Incident가 보이는지 확인합니다."),
    dict(title="남은 검색 횟수·비용", body="브리지 창의 검색 횟수와 비용 로그를 확인하고, 남은 횟수가 충분한지 확인합니다."),
    dict(title="화면 노출 점검", body="비밀번호·키·민감한 터미널 창이 화면에 노출되지 않도록 확인합니다."),
]

STOP = [
    dict(title="검색 브리지 종료", body="총 검색 횟수·비용을 기록합니다.", code=".\\scripts\\Start-OpenAiSearchBridge.ps1 -Stop"),
    dict(title="Spring 종료", body="서버를 종료합니다.", code=".\\scripts\\Stop-LlmTestServer.ps1"),
    dict(title="Vite·모의 provider 종료", body="각 실행 창에서 Ctrl+C를 누릅니다."),
    dict(title="방화벽 규칙 삭제", body="관리자 PowerShell에서\n삭제합니다.", code="Remove-NetFirewallRule -DisplayName 'demo-5176'"),
    dict(title="포트 종료 확인", body="시연용 포트가 모두\n닫혔는지 확인합니다.", code="Get-NetTCPConnection -State Listen -LocalPort 18090,18181,18282,5176"),
    dict(title="실제 비용 확인", body="OpenAI 사용량 화면에서 오늘 사용한 비용을 확인합니다."),
]

DEPLOY = [
    dict(title="HTTPS", body="서비스를 HTTPS로 제공해 secure context 문제를 제거합니다."),
    dict(title="정적 프론트 배포", body="npm run build 결과물을 정적 배포합니다. Vite 개발 서버 대신 배포용 환경을 사용합니다."),
    dict(title="동일 도메인", body="프론트와 API를 같은 도메인으로 묶어 CORS 설정을 최소화합니다."),
    dict(title="JWT 비밀값", body="JWT_SECRET을 비밀 저장소에서 주입합니다. 개발용 기본값을 사용하지 않습니다."),
    dict(title="계정·관리자 인증", body="데모 계정을 제거하고 회원 DB와 관리자 MFA를 적용합니다."),
    dict(title="업무·예산 영속 저장", body="H2 메모리 DB를 영속 DB로 전환하고 예산 장부도 영속 저장합니다."),
    dict(title="H2 콘솔", body="운영 배포에서 H2 콘솔을 비활성화합니다."),
    dict(title="배포 전 회귀 검증", body="프론트 테스트·빌드와 백엔드 테스트를 모두 통과합니다.",
         code="npm run test:intake\nnpm run test:operator\nnpm run build\n.\\gradlew.bat test"),
]


def write_source():
    def cards(items):
        result = []
        for item in items:
            result.append(f"- [ ] **{item['title']}**: {item['body']}")
            if item.get("code"):
                result.extend(["", TICK * 3 + "powershell", item["code"], TICK * 3])
            if item.get("note"):
                result.append("\n" + item["note"])
            result.append("")
        return "\n".join(result)

    source = [
        "# 시연·배포 서버 체크리스트",
        "",
        f"## 회고 | {DATE}",
        "",
        "오늘 오류 11건 중 코드 버그는 2건뿐이고, 나머지 9건은 실행 위치·순서·접속 주소 등 실행 환경 문제였습니다.",
        "",
        '> 핵심 교훈: "내 PC에서 된다"가 "다른 PC에서 된다"를 보장하지 않는다.',
        "",
        "오류 건수와 수정 완료 상태는 제공받은 회고 기록 기준입니다. 이 문서는 실행 절차를 정리한 것이며, 실제 시연 직전 점검은 별도로 수행합니다.",
        "",
        "## 오늘 겪은 오류",
        "",
        "| 증상 | 원인 | 예방 |",
        "| --- | --- | --- |",
    ]
    source += ["| " + " | ".join(row[:3]) + " |" for row in ERRORS]
    source += [
        "", "## 사전 준비 (시연 전날)", "",
        "시작 스크립트의 STOP 조건을 미리 없애는 단계입니다.", "",
        cards(PREP),
        "## 기동 순서 (창 4개, 이 순서 그대로)", "",
        "각 단계는 앞 단계가 실행 중이어야 시작됩니다. 순서가 바뀌면 스크립트가 STOP으로 멈춥니다.", "",
        cards(START),
        "CORS나 프록시 값을 바꾸면 해당 서버를 끄고 다시 시작해야 반영됩니다. Spring을 재시작하면 메모리 DB와 예산 장부도 초기화됩니다.", "",
        "## 외부 PC 접속", "",
        "외부에는 5176 하나만 엽니다. Spring(18090)과 브리지(18282)는 127.0.0.1에 두고 Vite 프록시를 통해 연결합니다.", "",
        cards(LAN),
        "브라우저는 HTTP LAN 주소를 보안 연결로 보지 않아 일부 기능을 제한합니다. 반드시 다른 PC에서 끝까지 확인합니다.", "",
        "방화벽 규칙의 -Profile은 네트워크 프로필과 맞아야 합니다. Get-NetConnectionProfile 결과가 Public이면 -Profile Public으로 바꿉니다.", "",
        "## 시연 직전 스모크 테스트 (10분)", "",
        "시연에서 보여 줄 흐름을 시연 PC에서 한 번씩 끝까지 확인합니다. 유료 검색은 1회(약 $0.01)만 사용하는 계획이며 실제 청구액은 사용량 화면에서 확인합니다.", "",
        cards(SMOKE),
        "하나라도 실패하면 오늘 겪은 오류 표에서 증상을 찾습니다. 해결되지 않으면 시연 영상으로 대체합니다.", "",
        "## 종료·정리", "",
        "시연이 끝나면 열어 둔 포트와 유료 프로세스를 바로 닫습니다.", "",
        cards(STOP),
        "## 운영 배포로 갈 때 추가할 것", "",
        "HTTPS와 고정 배포 환경으로 실행 환경 차이를 줄입니다.", "",
        cards(DEPLOY),
        "테스트 실행 위치: 프론트 명령은 frontend\\legal-procedure-viewer, Gradle 명령은 backend\\guardrail-ready-policy-checker입니다.", "",
    ]
    SOURCE.parent.mkdir(parents=True, exist_ok=True)
    SOURCE.write_text("\n".join(source), encoding="utf-8")


class Document:
    def __init__(self):
        self.c = canvas.Canvas(str(OUT), pagesize=(W, H))
        self.c.setTitle("시연·배포 서버 체크리스트 | 회고")
        self.c.setAuthor("Legal AI Intake Project")
        self.c.setSubject("오류 11건 회고와 시연·배포 체크리스트")
        self.page = 0
        self.bounds = []
        self.outline = []

    def text(self, value, x, y, width, size=17, color=TEXT, bold=False):
        style = ParagraphStyle(
            "body", fontName="KRB" if bold else "KR", fontSize=size,
            leading=size * 1.5, textColor=HexColor(color),
            wordWrap="CJK", splitLongWords=True,
        )
        markup = escape(str(value)).replace("\\", '<font name="Mono">\\</font>')
        para = Paragraph(markup.replace("\n", "<br/>"), style)
        _, height = para.wrap(width, 3000)
        assert x >= 0 and x + width <= W, (self.page, value, x, width)
        assert y >= 0 and y + height <= 907, (self.page, value, y, height)
        para.drawOn(self.c, x, H - y - height)
        self.bounds.append(dict(page=self.page, x=x, y=y, w=width, h=height))
        return height

    def rect(self, x, y, width, height, fill=PANEL, stroke=LINE, radius=12):
        self.c.setFillColor(HexColor(fill))
        self.c.setStrokeColor(HexColor(stroke))
        self.c.roundRect(x, H-y-height, width, height, radius, fill=1, stroke=1)

    def begin(self, title, lead, section):
        if self.page:
            self.c.showPage()
        self.page += 1
        self.c.setFillColor(HexColor(BG))
        self.c.rect(0, 0, W, H, fill=1, stroke=0)
        self.text("LEGAL AI INTAKE / DEMO & DEPLOYMENT RETROSPECTIVE", 48, 25, 1184, 12, CYAN, True)
        self.text(title, 48, 67, 1184, 32, TEXT, True)
        self.text(lead, 50, 122, 1180, 16, MUTED)
        self.c.setStrokeColor(HexColor(LINE))
        self.c.line(48, 48, 1232, 48)
        self.c.setFont("KR", 11)
        self.c.setFillColor(HexColor(MUTED))
        self.c.drawString(48, 28, f"{DATE}  |  {section}  |  4:3 DARK TABLET")
        self.c.drawRightString(1232, 28, f"{self.page:02d} / 09")
        key = f"page-{self.page}"
        self.c.bookmarkPage(key)
        self.c.addOutlineEntry(title, key, 0)
        self.outline.append(title)

    def code(self, value, x, y, width, size=15):
        lines = str(value).splitlines()
        # Preserve copyable command lines; never silently wrap a PowerShell token.
        for line in lines:
            if any(ord(c) > 127 for c in line):
                font = "KR"
            else:
                font = "Mono"
            assert pdfmetrics.stringWidth(line, font, size) <= width - 32, (line, width, size)
        height = len(lines) * (size * 1.45) + 26
        self.rect(x, y, width, height, CODE, LINE, 8)
        for index, line in enumerate(lines):
            font = "KR" if any(ord(c) > 127 for c in line) else "Mono"
            self.c.setFont(font, size)
            self.c.setFillColor(HexColor("#c9e4ff"))
            self.c.drawString(x+16, H-y-20-size-index*size*1.45, line)
        assert y + height <= 899, (self.page, value, y, height)
        self.bounds.append(dict(page=self.page, x=x, y=y, w=width, h=height, code=True))
        return height

    def card(self, item, x, y, width, height=None, size=17, checkbox=True, code_size=15):
        heading_x = x + (43 if checkbox else 20)
        header_h = 27
        # Measure without painting to compute each card's natural height.
        style = ParagraphStyle("measure", fontName="KR", fontSize=size, leading=size*1.5, wordWrap="CJK")
        body = Paragraph(escape(item["body"]), style)
        _, body_h = body.wrap(width-40, 3000)
        code_h = len(item.get("code", "").splitlines()) * (code_size * 1.45) + 26 if item.get("code") else 0
        note_h = 30 if item.get("note") else 0
        natural = 18 + header_h + body_h + (12 + code_h if code_h else 0) + (12 + note_h if note_h else 0) + 18
        actual = height or natural
        assert natural <= actual + 1, (self.page, item["title"], natural, actual)
        self.rect(x, y, width, actual)
        if checkbox:
            self.c.setStrokeColor(HexColor(CYAN))
            self.c.setLineWidth(1.4)
            self.c.roundRect(x+18, H-y-35, 15, 15, 2, stroke=1, fill=0)
        self.text(item["title"], heading_x, y+13, width-(heading_x-x)-20, size+1, CYAN, True)
        by = y+18+header_h
        self.text(item["body"], x+20, by, width-40, size)
        by += body_h
        if item.get("code"):
            by += 12
            by += self.code(item["code"], x+20, by, width-40, code_size)
        if item.get("note"):
            self.text(item["note"], x+20, by+12, width-40, 15, GOLD)
        return actual

    def band(self, title, body, y, height=90, color=GOLD):
        self.rect(48, y, 1184, height, "#202b3b", color)
        self.text(title, 66, y+12, 1148, 18, color, True)
        self.text(body, 66, y+43, 1148, 16, TEXT)


def build():
    for name, filename in [("KR", "malgun.ttf"), ("KRB", "malgunbd.ttf"), ("Mono", "consola.ttf")]:
        pdfmetrics.registerFont(TTFont(name, "C:/Windows/Fonts/" + filename))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    QA.mkdir(parents=True, exist_ok=True)
    write_source()
    d = Document()

    # 1. Retrospective / key lesson.
    d.begin("시연·배포 서버 체크리스트", "오늘의 오류를 다음 시연의 확인 항목으로 바꾸는 회고", "회고")
    for index, (number, title, detail, color) in enumerate([
        ("11", "오늘 겪은 오류", "기록된 전체 오류", BLUE),
        ("2", "코드 버그", "버튼 CSS · UUID 생성", CYAN),
        ("9", "실행 환경 문제", "실행 위치·순서·접속 주소 등", GOLD),
    ]):
        x = 48 + index*400
        d.rect(x, 185, 384, 185)
        d.text(number, x+22, 201, 130, 57, color, True)
        d.text(title, x+166, 220, 195, 21, TEXT, True)
        d.text(detail, x+22, 318, 340, 15, MUTED)
    d.text('"내 PC에서 된다"가', 64, 419, 1152, 40, TEXT, True)
    d.text('"다른 PC에서 된다"를 보장하지 않는다.', 64, 482, 1152, 40, CYAN, True)
    d.text("같은 서버·같은 네트워크에서도 다른 PC의 브라우저로 로그인과 문의 접수까지 확인합니다.", 66, 570, 1140, 20, MUTED)
    d.rect(48, 657, 1184, 155)
    d.text("다음 시연의 실행 순서", 69, 675, 1140, 21, CYAN, True)
    d.text("사전 준비 → 모의 provider → 검색 브리지 → Spring → Vite", 69, 719, 1140, 24, TEXT, True)
    d.text("외부 PC 접속 → 10분 스모크 테스트 → 시연 → 종료·비용 확인 → 운영 배포 보완", 69, 765, 1140, 18, MUTED)
    d.text("오류 건수·수정 완료 상태는 제공된 회고 기록 기준입니다. 실제 시연 직전 점검은 별도로 수행합니다.", 50, 851, 1180, 13, MUTED)

    # 2. The complete eleven-row issue table.
    d.begin("오늘 겪은 오류 11건", "코드 버그 2건과 실행 환경 문제 9건을 구분해 재발 방지 항목으로 남깁니다.", "오류 기록")
    xs = [48, 300, 772, 1232]
    y = 180
    d.rect(48, y, 1184, 43, "#223650", LINE, 7)
    for i, heading in enumerate(["증상", "원인", "예방"]):
        d.text(heading, xs[i]+12, y+9, xs[i+1]-xs[i]-24, 16, CYAN, True)
    y += 43
    for index, row in enumerate(ERRORS):
        style = ParagraphStyle("row", fontName="KR", fontSize=17, leading=25.5, wordWrap="CJK")
        heights = []
        for i, value in enumerate(row[:3]):
            p = Paragraph(escape(value), style)
            heights.append(p.wrap(xs[i+1]-xs[i]-24, 3000)[1])
        rh = max(52, max(heights) + 18)
        fill = "#192d39" if row[3] == "코드" else (PANEL if index % 2 == 0 else "#121d2e")
        d.rect(48, y, 1184, rh, fill, LINE, 0)
        for i, value in enumerate(row[:3]):
            d.text(value, xs[i]+12, y+9, xs[i+1]-xs[i]-24, 17, CYAN if row[3] == "코드" and i == 0 else TEXT, i == 0)
        y += rh
    assert y <= 891, y

    # 3. Preparation, compact cards paired by task.
    d.begin("사전 준비 | 시연 전날", "시작 스크립트가 STOP으로 멈추는 조건을 미리 없앱니다. 실행 위치는 저장소 루트 기준입니다.", "사전 준비")
    y = 178
    # Full-width rows keep paths and commands readable and copyable.
    for item in PREP[:6]:
        # A compact label/body row, with shell commands in a separate right column.
        body = item["body"]
        has_code = bool(item.get("code"))
        row_h = 84 if has_code else 69
        d.rect(48, y, 1184, row_h)
        d.text(item["title"], 67, y+12, 160, 17, CYAN, True)
        d.text(body, 237, y+11, 450 if has_code else 970, 15.5)
        if has_code:
            d.code(item["code"], 714, y+9, 499, 12)
        y += row_h+7
    for item in PREP[6:]:
        row_h = {"포트": 76, "서버 IP": 64, "남은 크레딧": 52}[item["title"]]
        d.rect(48, y, 1184, row_h)
        d.text(item["title"], 67, y+10, 160, 17, CYAN, True)
        if item["title"] == "포트":
            d.text(item["body"], 237, y+7, 970, 14)
            d.text(item["code"], 237, y+35, 970, 13, BLUE)
        elif item["title"] == "서버 IP":
            d.text(item["body"]+"  확인: ipconfig", 237, y+10, 970, 14.5)
        else:
            d.text(item["body"], 237, y+13, 970, 15.5)
        y += row_h+7
    assert y <= 900, y

    # 4. First two startup windows.
    d.begin("기동 순서 1/2 | 모의 → 브리지", "창 4개를 순서대로 실행합니다. 창 ①~③은 저장소 루트에서 시작합니다.", "기동 순서")
    d.card(START[0], 48, 189, 1184, 238, 21, False)
    d.card(START[1], 48, 456, 1184, 265, 21, False)
    d.band("① → ② → ③ → ④", "앞 단계가 실행 중인 상태에서 다음 단계를 시작합니다. 순서가 바뀌면 스크립트가 STOP으로 멈춥니다.", 760, 108)

    # 5. Spring and Vite, including complete PowerShell commands.
    d.begin("기동 순서 2/2 | Spring → Vite", "CORS는 Spring 시작 전, API_PROXY_TARGET은 Vite 시작 전에 같은 창에서 설정합니다.", "기동 순서")
    d.card(START[2], 48, 179, 1184, 330, 17, False)
    d.card(START[3], 48, 529, 1184, 233, 17, False)
    d.band("설정을 바꾸면 해당 서버를 재시작", "CORS·프록시 변경은 재기동 후 반영됩니다. Spring 재시작 시 메모리 DB와 예산 장부도 초기화됩니다.", 789, 96)

    # 6. LAN access, vector network diagram and checks.
    d.begin("외부 PC 접속 | 5176 하나만", "Spring(18090)과 브리지(18282)는 127.0.0.1에 두고 Vite 프록시를 통해 연결합니다.", "외부 PC")
    for x, title, body, width in [
        (48, "시연 PC", "같은 Wi-Fi / 스위치", 260),
        (364, "Vite : 5176", "LAN 공개 · --host 0.0.0.0", 330),
        (750, "서버 PC 내부", "127.0.0.1\nSpring 18090 / 브리지 18282", 482),
    ]:
        d.rect(x, 179, width, 95)
        d.text(title, x+17, 190, width-34, 19, CYAN, True)
        d.text(body, x+17, 227, width-34, 15, MUTED)
    for x1, x2 in [(309, 359), (695, 745)]:
        d.c.setStrokeColor(HexColor(CYAN))
        d.c.line(x1, H-225, x2, H-225)
        d.c.line(x2-7, H-220, x2, H-225)
        d.c.line(x2-7, H-230, x2, H-225)
    y = 296
    for index, item in enumerate(LAN):
        # Compact full-width cards keep the complete firewall command visible.
        d.rect(48, y, 1184, [164, 91, 142, 91][index])
        d.text(item["title"], 67, y+10, 1120, 18, CYAN, True)
        bh = d.text(item["body"], 67, y+43, 1146, 15.5)
        if item.get("code"):
            d.code(item["code"], 67, y+50+bh, 1146, 13.5)
        y += [164, 91, 142, 91][index] + 10
    d.text("HTTP LAN 주소는 secure context가 아니므로 다른 PC에서 끝까지 검증합니다. 네트워크가 Public이면 방화벽의 -Profile도 Public으로 바꿉니다.", 50, 831, 1180, 15, GOLD)

    # 7. The smoke test.
    d.begin("시연 직전 스모크 테스트 | 10분", "시연 PC에서 실제 흐름을 끝까지 확인합니다. 유료 검색은 1회(약 $0.01)만 사용하는 계획입니다.", "최종 점검")
    y = 179
    for index, item in enumerate(SMOKE):
        rh = 96 if index == 0 else 70
        d.rect(48, y, 1184, rh)
        d.c.setStrokeColor(HexColor(CYAN))
        d.c.roundRect(66, H-y-35, 15, 15, 2, stroke=1, fill=0)
        d.text(item["title"], 94, y+10, 219, 17, CYAN, True)
        d.text(item["body"], 323, y+10, 888, 16)
        if item.get("code"):
            d.code(item["code"], 323, y+39, 888, 13.5)
        y += rh+8
    d.band("실패하면 오류 표 확인 → 해결되지 않으면 시연 영상", "브리지 비용 로그와 OpenAI 사용량 화면을 함께 확인합니다. 약 $0.01은 계획값이며 실제 청구액과 다를 수 있습니다.", 827, 74)

    # 8. Shutdown.
    d.begin("종료·정리 | 유료 프로세스까지", "시연이 끝나면 열어 둔 포트와 유료 프로세스를 바로 닫습니다.", "종료")
    y = 179
    for item in STOP:
        rh = 111 if item.get("code") else 76
        d.rect(48, y, 1184, rh)
        d.text(item["title"], 68, y+12, 1142, 19, CYAN, True)
        d.text(item["body"], 68, y+45, 250 if item.get("code") else 1142, 16)
        if item.get("code"):
            d.code(item["code"], 350, y+48, 861, 13.5)
        y += rh+12
    assert y <= 900, y

    # 9. Production handoff.
    d.begin("운영 배포로 갈 때 추가할 것", "HTTPS와 고정 배포 환경으로 실행 환경 차이를 줄이고, 계정·데이터·예산을 운영 수준으로 보완합니다.", "운영 전환")
    for index, item in enumerate(DEPLOY):
        x = 48 + (index % 2)*600
        y = 180 + (index // 2)*167
        d.card(item, x, y, 584, 152 if index != 7 else 202, 16, True, 13)
    d.text("테스트 실행 위치: 프론트는 frontend\\legal-procedure-viewer / Gradle은 backend\\guardrail-ready-policy-checker", 50, 886, 1180, 12, MUTED)

    d.c.save()
    reader = PdfReader(str(OUT))
    assert len(reader.pages) == 9, len(reader.pages)
    extracted = "\n".join(page.extract_text() or "" for page in reader.pages)
    for expected in [
        "11", "코드 버그", "9", "node_modules", "CORS_ALLOWED_ORIGIN_PATTERNS",
        "crypto.randomUUID", "Set-LocalOpenAiKey.ps1", "Get-NetTCPConnection",
        "Start-OpenAiSearchBridge.ps1", "MaxSearches 20", "SearchBridgePort 18282",
        "API_PROXY_TARGET", "--host 0.0.0.0", "New-NetFirewallRule",
        "192.168.10.48:5176/intake.html", "BLOCKED_JAILBREAK",
        "Stop-LlmTestServer.ps1", "Remove-NetFirewallRule", "JWT_SECRET",
        "MFA", "npm run test:intake", "npm run test:operator", "gradlew.bat test",
    ]:
        assert expected in extracted, expected
    (QA / "layout.json").write_text(json.dumps(
        dict(pages=len(reader.pages), titles=d.outline, bounds=d.bounds),
        ensure_ascii=False, indent=2), encoding="utf-8")
    (QA / "extracted.txt").write_text(extracted, encoding="utf-8")
    REPO_PDF.parent.mkdir(parents=True, exist_ok=True)
    REPO_PDF.write_bytes(OUT.read_bytes())
    print(json.dumps(dict(pdf=str(OUT), source=str(SOURCE), pages=len(reader.pages),
                          bytes=OUT.stat().st_size), ensure_ascii=False))


if __name__ == "__main__":
    build()
