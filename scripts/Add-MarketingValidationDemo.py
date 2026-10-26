"""Insert 60 seconds of supplied marketing evidence into the existing local edit.

No server requests; preserves the previous eight-minute video and encoded parts.
"""
from pathlib import Path
from hashlib import sha256
import argparse, json, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'output/presentation-2026-10-25/eight-minute-security'
OUT = ROOT / 'output/presentation-2026-10-25/nine-minute-marketing-security'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    assets, parts = OUT / 'assets', OUT / 'parts'
    assets.mkdir(parents=True, exist_ok=True)
    parts.mkdir(exist_ok=True)
    original = json.loads((BASE / 'timeline.json').read_text(encoding='utf-8'))
    report = PdfReader(args.report)
    evidence = next(im.image.convert('RGB') for im in report.pages[2].images if im.name.startswith('Im22.'))
    shots = []
    for old in original['shots'][:-1]:
        shots.append({**old, 'kind': 'existing-encoded', 'source': str(BASE / 'parts' / f'{old["index"]:02d}.mp4')})

    def font(size, bold=False):
        return ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf' if bold else 'C:/Windows/Fonts/malgun.ttf', size)

    def wrapped(draw, text, face, width):
        for paragraph in text.split('\n'):
            line = ''
            for ch in paragraph:
                if line and draw.textlength(line + ch, font=face) > width:
                    yield line
                    line = ch
                else:
                    line += ch
            yield line

    def frame(key, title, subtitle, crop, notes):
        canvas = Image.new('RGB', (1920, 1080), '#0e1725')
        d = ImageDraw.Draw(canvas)
        d.text((40, 25), title, font=font(39, True), fill='#e8eff7')
        d.text((42, 86), subtitle, font=font(25), fill='#6bd6e8')
        im = evidence.crop(crop)
        scale = min(1190 / im.width, 735 / im.height)
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.Resampling.LANCZOS)
        canvas.paste(im, (40 + (1190-im.width)//2, 167 + (735-im.height)//2))
        d.rounded_rectangle((1260,145,1880,935), radius=20, fill='#162438', outline='#2b405b', width=2)
        y = 171
        for heading, body in notes:
            d.text((1284,y), heading, font=font(28,True), fill='#6bd6e8')
            y += 46
            for line in wrapped(d, body, font(27), 568):
                d.text((1284,y), line, font=font(27), fill='#e8eff7')
                y += 40
            y += 25
        assert y < 940, (key, y)
        d.text((42,951), '실험 증거: 화면10 마케팅 집계 검증 보고서 3쪽 · 원본 실행 캡처 관련 행 확대', font=font(23), fill='#adbed2')
        d.text((42,995), '가상 고객 여정 → 실제 API·DB 집계 검증 | 실제 고객·매출·계약 전환 성과와 구분', font=font(24,True), fill='#70dfb2')
        d.text((42,1038), '2026-10-25 보고서 기록 재생 · 새 요청·과금 없음 · 무음 검토본', font=font(20), fill='#adbed2')
        dest = assets / f'{key}.png'
        canvas.save(dest)
        shots.append(dict(key=key,seconds=20,title=title,kind='marketing-evidence',source=str(dest),mode='가상 여정·실제 API 집계·기존 보고서 증거',reportPage=3,crop=list(crop)))

    frame('M1_journey', '화면10 · 고객의 문의 여정을 집계합니다', '보안 검증을 마친 뒤, 운영에 필요한 숫자를 확인합니다.', (0,610,525,760), [
        ('실험 조건', '가상 방문 40건을 3단계로 입력\n실제 C13 API·DB 집계와 대조'),
        ('집계 증가', '방문 +40 → 작성 시작 +28\n접수 +20 → 안내 확인 +16'),
        ('운영 의미', '어느 단계에 얼마나 도달했는지 확인합니다. 단계별 수를 이탈률로 단정하지 않습니다.')])
    frame('M2_measure', '화면10 · 검색 횟수와 문의 수를 구분', '숫자가 무엇을 세는지 알아야 운영 판단이 정확해집니다.', (0,785,1211,942), [
        ('검색 집계', '검색 요청 +13회\n검색을 요청한 고유 문의 +10건\n요청 저장만 검증 · 외부 실행 없음'),
        ('상담 준비', '6건 준비 → 1건 종료 → 현재 5건\n계약 전환·상담 완료 건수와 다릅니다.'),
        ('분류 대조', '신규 문의 20건의 사건 유형·희망 단계·지역 분포도 모두 일치')])
    frame('M3_integrity', '화면10 · 잘못된 요청은 집계에서 제외', '운영자가 믿고 볼 수 있는 숫자인지 검증합니다.', (0,211,1211,594), [
        ('관측 결과', '권한 위반·조작 요청 10건 거절\n전체 검증 20/20 통과'),
        ('중복 방지', '같은 이벤트 3회 재전송\n→ created=false · 중복 집계 없음\n같은 ID·다른 내용은 409'),
        ('핵심 메시지', '고객은 선택하고, 시스템은 정리하며 운영자는 검증된 숫자로 흐름을 봅니다.')])
    mobile = original['shots'][-1]
    shots.append({**mobile, 'kind':'existing-encoded', 'source':str(BASE/'parts'/f'{mobile["index"]:02d}.mp4')})
    elapsed = 0
    for i, shot in enumerate(shots, 1):
        shot.update(index=i,start=elapsed,end=elapsed+shot['seconds'])
        elapsed += shot['seconds']
    assert elapsed == 540
    data = {**original, 'target':'LEGAL_INTAKE_DEMO_9M_MARKETING_SECURITY_SILENT.mp4','seconds':540,'marketingSeconds':60,'otherSeconds':240,
            'previousVideo':str(BASE/original['target']),'marketingReport':str(args.report),'marketingReportSha256':sha256(args.report.read_bytes()).hexdigest(),
            'marketingEvidencePixelSha256':sha256(evidence.tobytes()).hexdigest(),'shots':shots}
    (OUT/'timeline.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    def stamp(s): return f'{s//3600:02d}:{s%3600//60:02d}:{s%60:02d},000'
    (OUT/'CAPTIONS.srt').write_text('\n'.join(f'{s["index"]}\n{stamp(s["start"])} --> {stamp(s["end"])}\n{s["title"]}\n{s["mode"]}\n' for s in shots),encoding='utf-8-sig')
    if args.prepare_only:
        print('PREPARED 30 shots, 540 seconds; marketing 465–525 seconds')
        return
    sys.path.insert(0,str(ROOT/'tmp/video-tools'))
    import imageio_ffmpeg
    exe = imageio_ffmpeg.get_ffmpeg_exe()
    def run(options):
        p = subprocess.run([exe,'-hide_banner','-loglevel','error','-y',*options],capture_output=True)
        if p.returncode: raise RuntimeError(p.stderr.decode('utf-8',errors='replace'))
    for shot in shots:
        dest = parts/f'{shot["index"]:02d}.mp4'
        if shot['kind'] == 'existing-encoded':
            shutil.copy2(shot['source'],dest)
        else:
            run(['-loop','1','-framerate','10','-i',shot['source'],'-t',str(shot['seconds']),'-r','10','-an','-c:v','libx264','-pix_fmt','yuv420p','-preset','veryfast','-crf','20','-threads','4','-movflags','+faststart',str(dest)])
        print(f'{shot["index"]:02d}/30 {shot["key"]}',flush=True)
    listing = parts/'concat.txt'
    listing.write_text('\n'.join(f"file '{s['index']:02d}.mp4'" for s in shots),encoding='utf-8')
    run(['-f','concat','-safe','0','-i',str(listing),'-c','copy','-movflags','+faststart',str(OUT/data['target'])])
    print('DONE '+str(OUT/data['target']))

if __name__ == '__main__': main()
