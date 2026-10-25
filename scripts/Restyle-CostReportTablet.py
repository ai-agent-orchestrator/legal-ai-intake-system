"""Reflow a supplied report into the project's dark 4:3 tablet PDF theme.

Source tables and embedded captures are preserved, not regenerated as evidence.
All extracted source material and QA captures stay in ignored tmp/output folders.
"""
import argparse
from collections import Counter
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import re
import shutil
import subprocess
from xml.sax.saxutils import escape

import pdfplumber
from PIL import Image as PILImage, ImageDraw
from pypdf import PdfReader
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, Flowable, Frame, Image, KeepTogether, PageBreak,
    PageTemplate, Paragraph, Spacer, Table, TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
W, H = 1024, 768
BG, PANEL, LINE = '#0e1725', '#162438', '#2b405b'
TEXT, MUTED, CYAN, BLUE = '#e8eff7', '#adbed2', '#6bd6e8', '#68a8ff'
WIDTH = 932
QA = ROOT / 'tmp/pdfs/cost-report-tablet'
QA.mkdir(parents=True, exist_ok=True)
pdfmetrics.registerFont(TTFont('TabletKorean', 'C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('TabletKoreanBold', 'C:/Windows/Fonts/malgunbd.ttf'))
pdfmetrics.registerFontFamily('TabletKorean', normal='TabletKorean', bold='TabletKoreanBold')
STYLES = {
    'body': ParagraphStyle('body', fontName='TabletKorean', fontSize=15, leading=23, textColor=colors.HexColor(TEXT), spaceAfter=11, wordWrap='CJK'),
    'heading': ParagraphStyle('heading', fontName='TabletKoreanBold', fontSize=24, leading=34, textColor=colors.HexColor(CYAN), spaceBefore=7, spaceAfter=15, keepWithNext=True, wordWrap='CJK'),
    'sub': ParagraphStyle('sub', fontName='TabletKoreanBold', fontSize=18, leading=27, textColor=colors.HexColor(BLUE), spaceBefore=10, spaceAfter=10, keepWithNext=True, wordWrap='CJK'),
    'caption': ParagraphStyle('caption', fontName='TabletKorean', fontSize=12, leading=18, textColor=colors.HexColor(MUTED), spaceBefore=4, spaceAfter=11, wordWrap='CJK'),
    'cell': ParagraphStyle('cell', fontName='TabletKorean', fontSize=13, leading=19, textColor=colors.HexColor(TEXT), wordWrap='CJK'),
    'smallcell': ParagraphStyle('smallcell', fontName='TabletKorean', fontSize=11, leading=16, textColor=colors.HexColor(TEXT), wordWrap='CJK'),
    'headcell': ParagraphStyle('headcell', fontName='TabletKoreanBold', fontSize=13, leading=19, textColor=colors.HexColor(CYAN), wordWrap='CJK'),
    'source': ParagraphStyle('source', fontName='TabletKorean', fontSize=10, leading=15, textColor=colors.HexColor(MUTED), spaceAfter=10),
}


def clean(text):
    text = re.sub(r'[\u2010-\u2015\u2212]', '-', str(text or ''))
    text = re.sub(r'\s+', ' ', text).strip()
    return re.sub(r'([Ee][+-])\s+(\d)', r'\1\2', text)


def para(text, style='body'):
    return Paragraph(escape(clean(text)), STYLES[style])


def contains(outer, inner):
    return outer[0] - .5 <= inner[0] and outer[1] - .5 <= inner[1] and outer[2] + .5 >= inner[2] and outer[3] + .5 >= inner[3]


def inside(box, x, y):
    return box[0] - .1 <= x <= box[2] + .1 and box[1] - .1 <= y <= box[3] + .1


def extract(source):
    reader, blocks, images = PdfReader(source), [], []
    with pdfplumber.open(source) as document:
        for index, page in enumerate(document.pages):
            tables = [table for table in page.find_tables() if any(clean(cell) for row in table.extract() for cell in row)]
            tables = [table for table in tables if not any(other is not table and contains(other.bbox, table.bbox) and other.bbox != table.bbox for other in tables)]
            elements = []
            for table in tables:
                rows = [[clean(cell) for cell in row] for row in table.extract()]
                # The command appendix contains spurious inline-code border columns.
                if index == 18 and len(rows[0]) == 3:
                    rows = [[row[0], clean(' '.join(row[1:]))] for row in rows]
                    widths = [160, WIDTH - 160]
                else:
                    first = table.rows[0].cells
                    widths = [((cell[2] - cell[0]) if cell else 1) for cell in first]
                    total = sum(widths)
                    widths = [value / total * WIDTH for value in widths]
                elements.append({'kind': 'table', 'top': table.bbox[1], 'rows': rows, 'widths': widths})
            pdf_images = {str(image.name).split('.')[0]: image for image in reader.pages[index].images}
            for image_number, item in enumerate(page.images, 1):
                name = item['name']
                embedded = pdf_images.get(name)
                if embedded is None:
                    raise ValueError(f'Missing embedded image page {index + 1}: {name}')
                # pypdf's exported .data can be a recompressed JPEG. Preserve decoded source pixels losslessly.
                decoded = embedded.image.convert('RGB')
                buffer = BytesIO(); decoded.save(buffer, format='PNG'); data = buffer.getvalue()
                record = {'sourcePage': index + 1, 'name': name, 'size': list(decoded.size), 'pixelsSha256': sha256(decoded.tobytes()).hexdigest()}
                images.append(record)
                elements.append({'kind': 'image', 'top': item['top'], 'bytes': data, 'size': decoded.size, 'record': record,
                                 'label': f'근거 자료 · 원문 {index + 1:02d} / 이미지 {image_number:02d}'})
            lines = []
            for line in page.extract_text_lines(return_chars=True):
                if re.fullmatch(r'\d+\s*/\s*\d+', clean(line['text'])) and line['top'] > 770:
                    continue
                if any(inside(table.bbox, (line['x0'] + line['x1']) / 2, (line['top'] + line['bottom']) / 2) for table in tables):
                    continue
                text = clean(line['text'])
                if not text:
                    continue
                size = max((char['size'] for char in line['chars']), default=8)
                style = 'heading' if size >= 10 else 'sub' if size >= 8.5 else 'caption' if size < 7.3 else 'body'
                if re.match(r'^\d+\.\d+\s', text):
                    style = 'sub'
                if re.match(r'^(그림|캡처)\s*\d+\.', text):
                    style = 'caption'
                lines.append({'kind': 'text', 'top': line['top'], 'bottom': line['bottom'], 'text': text, 'style': style})
            # Merge source physical line wraps, while keeping headings/captions and table boundaries distinct.
            all_elements = sorted(elements + lines, key=lambda item: (item['top'], item.get('kind', '')))
            merged = []
            for element in all_elements:
                if element['kind'] == 'text' and merged and merged[-1]['kind'] == 'text':
                    previous = merged[-1]
                    if element['style'] == previous['style'] and element['style'] in ('body', 'caption') and element['top'] - previous['bottom'] < 7 and not re.match(r'^(그림|캡처)\s*\d+\.', element['text']):
                        previous['text'] = clean(previous['text'] + ' ' + element['text'])
                        previous['bottom'] = element['bottom']
                        continue
                merged.append(element)
            blocks.append(merged)
    return reader, blocks, images


class Cover(Flowable):
    def __init__(self, original):
        super().__init__()
        self.width, self.height, self.original = WIDTH, 528, original

    def draw(self):
        c = self.canv
        c.setFillColor(colors.HexColor(CYAN)); c.roundRect(0, 458, 94, 5, 2, fill=1, stroke=0)
        c.setFont('TabletKoreanBold', 39); c.setFillColor(colors.HexColor(TEXT))
        c.drawString(0, 398, 'AI 비용 통제')
        c.drawString(0, 341, '실험 보고서')
        c.setFont('TabletKorean', 18); c.setFillColor(colors.HexColor(MUTED))
        c.drawString(0, 292, '실제 호출·차단·정산을 증거로 확인한 기록')
        c.setFillColor(colors.HexColor(PANEL)); c.roundRect(0, 12, WIDTH, 229, 12, fill=1, stroke=0)
        p = para(self.original, 'body'); _, height = p.wrap(WIDTH - 40, 220)
        if height > 205:
            raise ValueError(f'Cover source text too long: {height}')
        p.drawOn(c, 20, 223 - height)


def story_for(blocks):
    story = []
    for number, elements in enumerate(blocks, 1):
        if number > 1:
            story.append(PageBreak())
        story.append(para(f'원문 {number:02d} / {len(blocks):02d} · 수치와 실험 판정은 제공된 보고서 기준', 'source'))
        if number == 1:
            story.append(Cover(' '.join(item['text'] for item in elements if item['kind'] == 'text')))
            continue
        for item in elements:
            if item['kind'] == 'text':
                story.append(para(item['text'], item['style']))
            elif item['kind'] == 'table':
                checksum = any('SHA-256' in value for value in item['rows'][0])
                rows = [[para(cell, 'headcell' if i == 0 else 'smallcell' if checksum else 'cell') for cell in row] for i, row in enumerate(item['rows'])]
                table = Table(rows, colWidths=item['widths'], repeatRows=1, hAlign='LEFT')
                table.setStyle(TableStyle([
                    ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1b3a50')),
                    ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.HexColor(PANEL), colors.HexColor('#122033')]),
                    ('GRID', (0, 0), (-1, -1), .45, colors.HexColor(LINE)),
                    ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                    ('LEFTPADDING', (0, 0), (-1, -1), 12), ('RIGHTPADDING', (0, 0), (-1, -1), 12),
                    ('TOPPADDING', (0, 0), (-1, -1), 10), ('BOTTOMPADDING', (0, 0), (-1, -1), 10),
                ]))
                story.extend([table, Spacer(1, 14)])
            else:
                width, height = item['size']
                scale = min(WIDTH / width, 510 / height)
                image = Image(BytesIO(item['bytes']), width=width * scale, height=height * scale)
                image.hAlign = 'CENTER'
                story.extend([para(item['label'], 'sub'), image, Spacer(1, 12)])
    return story


def build_pdf(target, blocks, total=0):
    def page_theme(c, doc):
        c.saveState()
        c.setFillColor(colors.HexColor(BG)); c.rect(0, 0, W, H, fill=1, stroke=0)
        c.setFillColor(colors.HexColor(CYAN)); c.setFont('TabletKoreanBold', 11)
        c.drawString(46, 735, 'LEGAL AI INTAKE  /  COST CONTROL EXPERIMENT')
        c.setFillColor(colors.HexColor(MUTED)); c.setFont('TabletKorean', 10)
        c.drawRightString(W - 46, 735, '2026-10-25  |  DARK TABLET 4:3')
        c.setStrokeColor(colors.HexColor(LINE)); c.line(46, 719, W - 46, 719)
        c.line(46, 44, W - 46, 44)
        c.setFont('TabletKorean', 9); c.drawString(46, 27, '제공 원문 테마·배치 편집 / 캡처 원본 해상도 유지 / 새 실험·요금 재검증 없음')
        c.setFillColor(colors.HexColor(CYAN)); c.drawRightString(W - 46, 27, f'{doc.page:02d} / {total:02d}' if total else f'{doc.page:02d}')
        c.restoreState()
    doc = BaseDocTemplate(str(target), pagesize=(W, H), title='Legal AI Intake · 비용 통제 실험 보고서 · Dark Tablet', author='원문: Claude·프로젝트 담당자 / 테마 편집: Codex', leftMargin=46, rightMargin=46, topMargin=65, bottomMargin=60)
    doc.addPageTemplates(PageTemplate(id='dark', frames=Frame(46, 60, WIDTH, 646, leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0), onPage=page_theme))
    doc.build(story_for(blocks))


def verify(source, target, source_images):
    original, result = PdfReader(source), PdfReader(target)
    output_text = ''.join(page.extract_text() or '' for page in result.pages)
    source_text = ''.join(page.extract_text() or '' for page in original.pages)
    normalized = re.sub(r'\s+', '', clean(output_text))
    facts = ['0.0000684', '0.0000027', '0.00004725', '0.00001845', '132', '81', '276', 'request_precheck', '8.535E-5', 'gpt-4o-mini-2024-07-18', '기록없음']
    missing = [fact for fact in facts if fact not in normalized]
    hashes = set(re.findall(r'\b[a-f0-9]{64}\b', source_text))
    missing_hashes = [value for value in hashes if value not in normalized]
    source_pixel_hashes = Counter(item['pixelsSha256'] for item in source_images)
    output_pixel_hashes = Counter()
    for page in result.pages:
        assert list(map(float, page.mediabox))[2:] == [1024.0, 768.0]
        for image in page.images:
            decoded = image.image.convert('RGB')
            output_pixel_hashes[sha256(decoded.tobytes()).hexdigest()] += 1
    missing_images = [key for key in source_pixel_hashes if output_pixel_hashes[key] < source_pixel_hashes[key]]
    if missing or missing_hashes or missing_images:
        raise ValueError(f'Preservation failed: facts={missing}, hashes={len(missing_hashes)}, captures={len(missing_images)}')
    with pdfplumber.open(target) as document:
        for index, page in enumerate(document.pages):
            assert all(-.5 <= char['x0'] < char['x1'] <= W + .5 and -.5 <= char['top'] < char['bottom'] <= H + .5 for char in page.chars), f'Text outside page {index + 1}'
    proof = {'sourceSha256': sha256(Path(source).read_bytes()).hexdigest(), 'sourcePages': len(original.pages), 'outputPages': len(result.pages), 'pageSize': [W, H], 'embeddedSourceImages': len(source_images), 'allSourceImagePixelsPreserved': not missing_images, 'sourceHashStringsPreserved': len(hashes), 'keyFactsPreserved': facts, 'originalOverwritten': False}
    (QA / 'verification.json').write_text(json.dumps(proof, ensure_ascii=False, indent=2), encoding='utf-8')
    return proof


def render(target):
    command = shutil.which('pdftoppm')
    if not command:
        raise RuntimeError('pdftoppm is required for visual QA')
    subprocess.run([command, '-png', '-scale-to', '1024', str(target), str(QA / 'page')], check=True, capture_output=True)
    count = len(PdfReader(target).pages)
    files = sorted(QA.glob('page-*.png'))[:count]
    for stale in QA.glob('contact-*.png'):
        if int(stale.stem.split('-')[-1]) > (count + 11) // 12:
            stale.unlink()
    for batch in range(0, len(files), 12):
        selected = files[batch:batch + 12]
        sheet = PILImage.new('RGB', (1024, ((len(selected) + 1) // 2) * 404), BG)
        draw = ImageDraw.Draw(sheet)
        for index, file in enumerate(selected):
            image = PILImage.open(file).convert('RGB'); image.thumbnail((500, 375))
            x, y = (index % 2) * 512, (index // 2) * 404
            sheet.paste(image, (x, y)); draw.text((x + 12, y + 380), f'PAGE {batch + index + 1:02d}', fill='white')
        sheet.save(QA / f'contact-{batch // 12 + 1:02d}.png')


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--source', required=True); parser.add_argument('--output', required=True)
    args = parser.parse_args(); source, target = Path(args.source), Path(args.output)
    if source.resolve() == target.resolve():
        raise ValueError('Preserve the original: choose another output path')
    target.parent.mkdir(parents=True, exist_ok=True)
    reader, blocks, images = extract(source)
    (QA / 'source-images.json').write_text(json.dumps(images, ensure_ascii=False, indent=2), encoding='utf-8')
    first = QA / 'first-pass.pdf'; build_pdf(first, blocks)
    total = len(PdfReader(first).pages); build_pdf(target, blocks, total)
    proof = verify(source, target, images); render(target)
    print(json.dumps({'output': str(target), **proof}, ensure_ascii=False))


if __name__ == '__main__':
    main()
