"""Keep the three editable backend Mermaid flows in the main README."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / 'docs/presentation-2026-10-25/BACKEND_CONCRETE_FLOWS.md'
readme = ROOT / 'README.md'
start = '<!-- BACKEND_CONCRETE_FLOWS:START -->'
end = '<!-- BACKEND_CONCRETE_FLOWS:END -->'
body = source.read_text(encoding='utf-8').split('## 1.', 1)[1].split('## 코드 대조 진입점', 1)[0]
body = '## 1.' + body
body = body.replace('\n## ', '\n### ')
if body.startswith('## '): body = '#' + body
section = (start + '\n## 백엔드 구체적 흐름\n\n'
           'HTTP 진입점 → 서비스·정책 → 저장·응답·관측을 세 흐름으로 연결했습니다. 현재 소스 기준이며 새 실행 검수 결과는 아닙니다.\n\n'
           + body.strip() + '\n\n'
           '[편집 원본·코드 대조 진입점](docs/presentation-2026-10-25/BACKEND_CONCRETE_FLOWS.md) · '
           '[제품 소개 PDF 11~13쪽](docs/presentation-pdfs/PRODUCT_INTRODUCTION_DARK_TABLET.pdf)\n' + end + '\n\n')
text = readme.read_text(encoding='utf-8')
if start in text:
    before, rest = text.split(start, 1)
    _, after = rest.split(end, 1)
    text = before + section.rstrip() + after
else:
    text = text.replace('## 핵심 API\n', section + '## 핵심 API\n', 1)
readme.write_text(text, encoding='utf-8')
print('README backend flows synchronized (3 diagrams)')
