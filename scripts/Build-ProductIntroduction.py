"""Editable Markdown/Mermaid source -> Korean 4:3 dark tablet introduction PDF."""
from pathlib import Path
from xml.sax.saxutils import escape
import re
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.lib.colors import HexColor

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/presentation-2026-10-25/00_PRODUCT_INTRODUCTION.md'
OUTPUT = ROOT / 'output/pdf/LEGAL_AI_INTAKE_PRODUCT_INTRO_DARK_TABLET.pdf'
W, H = 1024, 768
BG, PANEL, LINE = '#0e1725', '#162438', '#2b405b'
TEXT, MUTED, CYAN, BLUE, GREEN, RED = '#e8eff7', '#adbed2', '#6bd6e8', '#68a8ff', '#70dfb2', '#ff9eaa'

def render(source=SOURCE, output=OUTPUT, generic=False):
    pdfmetrics.registerFont(TTFont('Korean', 'C:/Windows/Fonts/malgun.ttf'))
    pdfmetrics.registerFont(TTFont('KoreanBold', 'C:/Windows/Fonts/malgunbd.ttf'))
    pages = []
    for section in re.split(r'^## ', source.read_text(encoding='utf-8'), flags=re.M)[1:]:
        lines = section.strip().splitlines()
        title = lines.pop(0)
        lead = lines.pop(0).strip()
        cards = []
        for card in re.split(r'^### ', '\n'.join(lines), flags=re.M)[1:]:
            heading, _, body = card.strip().partition('\n')
            cards.append((heading, body.strip()))
        pages.append((title, lead, cards))
    output.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(output), pagesize=(W, H))
    c.setTitle('Legal AI Intake System | 제품 소개')
    c.setAuthor('Legal AI Intake System')
    bounds = []

    def text(value, x, y, width, size=18, color=TEXT, bold=False):
        style = ParagraphStyle('body', fontName='KoreanBold' if bold else 'Korean',
                               fontSize=size, leading=size*1.48, textColor=HexColor(color), wordWrap='CJK')
        p = Paragraph(escape(value).replace('\n', '<br/>'), style)
        _, height = p.wrap(width, 1500)
        assert y + height < 718, (value, y, height)
        p.drawOn(c, x, H-y-height)
        bounds.append((x, y, width, height))
        return height

    def panel(x, y, width, height, heading, body, accent=CYAN, size=18):
        c.setFillColor(HexColor(PANEL)); c.setStrokeColor(HexColor(LINE))
        c.roundRect(x, H-y-height, width, height, 13, fill=1, stroke=1)
        c.setFillColor(HexColor(accent)); c.roundRect(x, H-y-height+18, 3, height-36, 1, fill=1, stroke=0)
        th = text(heading, x+20, y+18, width-40, 21, accent, True)
        bh = text(body, x+20, y+30+th, width-40, size)
        assert 30+th+bh <= height-14, (heading, height, th, bh)

    def arrow(x1, y1, x2, y2):
        c.setStrokeColor(HexColor(CYAN)); c.setLineWidth(2)
        c.line(x1, H-y1, x2, H-y2)
        c.line(x2-6, H-y2+6, x2, H-y2); c.line(x2+6, H-y2+6, x2, H-y2)

    def flow_box(x, y, width, height, heading, body, accent=BLUE, size=13, heading_size=16):
        c.setFillColor(HexColor(PANEL)); c.setStrokeColor(HexColor(accent))
        c.roundRect(x, H-y-height, width, height, 9, fill=1, stroke=1)
        th = text(heading, x+14, y+8, width-28, heading_size, accent, True)
        bh = text(body, x+14, y+11+th, width-28, size)
        assert 11+th+bh <= height-7, (heading, height, th, bh)

    def line_arrow(x1,y1,x2,y2):
        import math
        c.setStrokeColor(HexColor(CYAN)); c.setLineWidth(1.5)
        c.line(x1,H-y1,x2,H-y2)
        angle=math.atan2(y2-y1,x2-x1)
        for offset in (-.55,.55):
            c.line(x2,H-y2,x2-7*math.cos(angle+offset),H-(y2-7*math.sin(angle+offset)))

    def backend_page(title, cards):
        if title.startswith('백엔드 ①'):
            boxes=[(44,200,936,84),(44,302,936,84),(44,406,458,84),
                   (522,406,458,84),(44,515,936,84),(44,616,936,84)]
            for a,b in [((512,284),(512,302)),((512,386),(273,406)),
                        ((512,386),(751,406)),((273,490),(512,515)),
                        ((751,490),(512,515)),((512,599),(512,616))]: line_arrow(*a,*b)
            for j,((x,y,w,h),(heading,body)) in enumerate(zip(boxes,cards)):
                flow_box(x,y,w,h,heading,body,[BLUE,GREEN,CYAN,BLUE,GREEN,BLUE][j])
        elif title.startswith('백엔드 ②'):
            boxes=[(44,200,936,84),(44,304,458,84),(522,304,458,84),
                   (44,409,458,116),(522,409,458,116),(44,558,936,84)]
            for a,b in [((512,284),(273,304)),((512,284),(751,304)),
                        ((273,388),(273,409)),((751,388),(751,409)),
                        ((273,525),(512,558)),((751,525),(512,558))]: line_arrow(*a,*b)
            for j,((x,y,w,h),(heading,body)) in enumerate(zip(boxes,cards)):
                flow_box(x,y,w,h,heading,body,[BLUE,BLUE,BLUE,GREEN,GREEN,BLUE][j])
            text('expectedVersion 생략 시 버전 검사 없음 · 메모 재전송은 Idempotent-Replayed로 구분',
                 46,657,930,13,MUTED)
            text('메모 키가 없으면 기본120초 추정만 적용 · 같은 상태는200·changed=false·버전 유지',
                 46,685,930,13,MUTED)
        else:
            for j,(heading,body) in enumerate(cards[:6]):
                y=190+j*79
                flow_box(44,y,604,76,heading,body,[BLUE,GREEN,CYAN,GREEN,CYAN,BLUE][j],12,14)
                if j<5: line_arrow(346,y+76,346,y+79)
            flow_box(676,295,304,212,cards[6][0],cards[6][1],RED,13)
            for y in (304,380,456,532): line_arrow(648,y,676,401)
            text('SSE는 원본 토큰을 바로 중계하지 않고 내부 버퍼에 수집한 뒤 검사합니다.',
                 682,538,288,13,MUTED)
            text('출력이 차단돼도 이미 생성한 답변의 비용은 정산합니다.',682,613,288,13,MUTED)
            text('AiMetricRecorder · 비용/위험 메모리 → C16 /actuator/prometheus · 별도 ADMIN Basic',
                 46,676,930,14,CYAN,True)

    for i, (title, lead, cards) in enumerate(pages, 1):
        c.setFillColor(HexColor(BG)); c.rect(0, 0, W, H, fill=1, stroke=0)
        text('LEGAL AI INTAKE  /  PRODUCT BRIEF', 42, 24, 900, 12, CYAN, True)
        text(title, 42, 72, 940, 34, TEXT, True)
        text(lead, 44, 134, 930, 18, MUTED)
        if title.startswith('백엔드 '):
            backend_page(title,cards)
        elif generic or i >= 8:
            if len(cards) == 6:
                for j,(heading,body) in enumerate(cards):
                    panel(44+(j%3)*316,208+(j//3)*230,304,210,heading,body,[BLUE,GREEN,CYAN][j%3],17)
            elif len(cards) == 4:
                for j,(heading,body) in enumerate(cards):
                    panel(44+(j%2)*476,208+(j//2)*230,460,210,heading,body,[BLUE,GREEN,CYAN,RED][j],18)
            else:
                for j,(heading,body) in enumerate(cards):
                    panel(44,208+j*151,936,137,heading,body,[BLUE,GREEN,CYAN][j],18)
        elif i == 1:
            text('고객은 선택하고,\n챗봇은 정리한다.', 54, 230, 900, 52, CYAN, True)
            for j, (heading, body) in enumerate(cards):
                panel(44+j*316, 460, 304, 194, heading, body, [BLUE,GREEN,CYAN][j], 17)
        elif i in (2, 3):
            for j, (heading, body) in enumerate(cards):
                panel(44, 208+j*151, 936, 137, heading, body, [BLUE,GREEN,CYAN][j], 18)
        elif i == 4:
            for j, (heading, body) in enumerate(cards):
                panel(44+(j%3)*316, 208+(j//3)*230, 304, 210, heading, body, [BLUE,GREEN,CYAN][j%3], 17)
        elif i == 5:
            for j, (heading, body) in enumerate(cards[:3]):
                panel(44, 204+j*151, 600, 134, heading, body, [BLUE,GREEN,CYAN][j], 18)
                if j < 2: arrow(344, 339+j*151, 344, 353+j*151)
            panel(668, 204, 312, 428, cards[3][0], cards[3][1], CYAN, 18)
        elif i == 6:
            panel(44, 208, 448, 438, cards[0][0], cards[0][1], BLUE, 19)
            panel(512, 208, 468, 438, cards[1][0], cards[1][1], GREEN, 19)
        elif i == 7:
            for j, (heading, body) in enumerate(cards):
                panel(44+(j%2)*476, 208+(j//2)*230, 460, 210, heading, body, [BLUE,GREEN,CYAN,RED][j], 18)
        elif i == 8:
            panel(44, 208, 460, 370, cards[0][0], cards[0][1], GREEN, 19)
            panel(524, 208, 456, 370, cards[1][0], cards[1][1], BLUE, 19)
            text(cards[2][0], 50, 606, 920, 22, CYAN, True)
            text(cards[2][1], 50, 646, 920, 17)
        c.setStrokeColor(HexColor(LINE)); c.line(42, 52, 982, 52)
        c.setFillColor(HexColor(MUTED)); c.setFont('Korean', 11)
        c.drawString(44, 30, '2026-10-25  |  제품 소개  |  4:3 DARK TABLET')
        c.drawRightString(980, 30, f'{i:02d} / {len(pages):02d}')
        c.showPage()
    c.save()
    print(f'Created {output} ({len(pages)} pages, {len(bounds)} text blocks checked)')

if __name__ == '__main__':
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=Path,default=SOURCE)
    parser.add_argument('--output',type=Path,default=OUTPUT)
    parser.add_argument('--generic',action='store_true')
    args=parser.parse_args()
    render(args.source,args.output,args.generic)
