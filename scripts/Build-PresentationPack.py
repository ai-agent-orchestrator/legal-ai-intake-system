"""Three concise, editable Markdown + 4:3 dark tablet PDFs. No secret inputs."""
from pathlib import Path
import json, re, subprocess, shutil
from xml.sax.saxutils import escape
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.colors import HexColor
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from pypdf import PdfReader
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'docs/presentation-2026-10-25'; OUT=ROOT/'output/pdf'; QA=ROOT/'tmp/pdfs/presentation-2026-10-25'
for p in (DOC,OUT,QA):p.mkdir(parents=True,exist_ok=True)
pdfmetrics.registerFont(TTFont('Korean','C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('KoreanBold','C:/Windows/Fonts/malgunbd.ttf'))
BG='#0e1725'; PANEL='#162438'; LINE='#2b405b'; TEXT='#e8eff7'; MUTED='#adbed2'; CYAN='#6bd6e8'; BLUE='#68a8ff'; GREEN='#70dfb2'; RED='#ff9eaa'
W,H=1024,768
review=json.loads((ROOT/'output/q01-cross-role-2026-10-25/verification.json').read_text(encoding='utf-8-sig'))
demo_file=ROOT/'output/presentation-2026-10-25/demo-data.json'
demo=json.loads(demo_file.read_text(encoding='utf-8-sig')) if demo_file.exists() else {}
P=lambda title,lead,items=None,**kw:dict(title=title,lead=lead,items=items or [],**kw)
security=[('01 인증·업무 권한','JWT로 사용자 식별. 문의 소유자·배정 변호사·관리자를 서버에서 확인. 운영 업무는 ADMIN, 지표는 별도 Basic.'),('02 입력 계약','필수값·100자 사건 입력·코드·파일 유형·위험 URL을 서버에서 검증. 잘못된 입력은 저장 전에 중단.'),('03 Fixed Guard','법률 위해·탈옥·주입·비밀 요청·인증 우회를 규칙으로 먼저 차단. 이 경로에서는 LLM 생성을 호출하지 않음.'),('04 Dynamic Guard','요청 신호 조합과 대화 누적을 나란히 관찰. 요청 80점은 차단 권고, 누적 100점 등은 세션 제한.'),('05 입력·출력 Guardrail','생성 전 입력과 생성 후 출력을 검사. 이미 실행한 유료 단계 비용은 차단돼도 정산 대상.'),('06 개인정보 출력 보호','응답과 SSE 전송 전에 패턴 마스킹. 내부 상담 메모는 의뢰인 응답 JSON에서 제외.')]
cost=[('01 입력 범위 제한','사건 입력 100자. 입력 문자 수와 과금 토큰 수는 서로 다름. 시스템 지시문·검색 결과도 토큰에 포함.'),('02 출력 토큰 상한','생성 max_completion_tokens를 제한하고 최대 출력량으로 예약. 원격 Guard 출력 상한은 별도 확인 필요.'),('03 단계별 단가·예약','입력 Guard / 생성 / 출력 Guard / 검색문 / 설명 단계를 분리. 각 모델의 1,000토큰당 단가 적용.'),('04 공통 예산 확인','정산액 + 추정 사용액 + 진행 예약 + 신규 예약을 월(현재 프로세스 누적)·일·세션 한도와 비교. 부족하면 다음 호출 중단.'),('05 한 번만 정산·해제','usage 확인 시 토큰 기준, 실행 후 usage 미확인은 추정 정산. 미실행 예약만 해제. 중복 콜백 종료 방지.'),('06 관측·대사','C16에서 실사용·추정·정산·예약·단계 비용 확인. 공급자 청구서는 별도 대조하며 재시작 영속 원장은 아님.')]
product=[
P('상담은 더 명확하게.\nAI 운영은 더 안심되게.','Security-Aware Legal AI Intake System',[
('문제에서 시작한 Technical PM','고객의 법률 탐색 부담과 소규모 사무소의 초기 응대 비용을 함께 줄이는 서비스 흐름을 설계했습니다.'),
('핵심 가치','짧은 문의 → 규칙·DB 기반 안내 → 자료 준비 → 공개 정보 검색 → 변호사 검토 → 관리자 관측.'),
('오늘 보여줄 증거','역할별 권한, 6개 보안 통제, 6개 비용 통제, 직관적인 화면13, PC·390px 반응형 검수.')],note='2026-10-25 발표본 | 촬영 DB는 더미. 공급자 응답·usage는 모의이며 실제 청구 증거가 아닙니다.'),
P('누구의 어떤 비용을 줄이나','기능 나열보다 문제 → 판단 → 운영 지표를 연결합니다.',[
('의뢰인','내 사건이 어떤 유형인지, 어떤 자료가 필요한지 모르기 쉽습니다. 선택형 입력과 짧은 안내로 시작 부담을 낮춥니다.'),('변호사','상담 전에 사건 유형·당사자 위치·자료·희망 도움을 구조화해 확인합니다. 내부 검토 메모는 의뢰인과 분리합니다.'),('사무소 관리자','접수 현황·관심 분야·보안·비용을 한 흐름에서 확인합니다. 복잡한 지표를 위험 패턴과 조치 후보로 바꿉니다.'),('PM의 검증 기준','접수·검색·상담 준비 수는 확인 가능한 지표입니다. 상담 전환율 향상이나 시간 절감의 실증 수치는 아직 주장하지 않습니다.')]),
P('의뢰인에서 운영자까지, 하나의 여정','초반 안내에 매번 LLM을 부르지 않는 선택도 제품 설계입니다.',[
('화면01~04 | USER','로그인 → 100자 이내 사건 입력 → 규칙 분류·Reference 안내 → 접수. 같은 저장 시도에는 같은 요청 키를 사용합니다.'),('화면05 | 공개 정보 탐색','저장 조건 → LLM 검색문 → 네이버의 변협 대상 + 일반 웹 검색 → 결과 저장 → 근거 범위 내 관련성 설명.'),('화면06~08 | LAWYER','배정 목록 → 상세 → 상태·내부 메모. 버전 충돌409를 알리고 작성 내용은 보존한 뒤 명시적으로 복구합니다.'),('화면09~13 | ADMIN','운영 요약·마케팅·보안/비용·Incident·AI 위험 요약. 문의 배정은 ADMIN, 내부 메모는 담당자와 관리자만 접근합니다.')],note='변협은 네이버 인덱스의 공식 도메인 대상 검색입니다. 직접 변협 DB 조회·검증된 추천 순위가 아닙니다.'),
P('보안6단계 | 여섯 가지 통제','모든 API에 여섯 단계를 무조건 적용한다는 의미는 아닙니다.',security,grid=True,note='업무 API는 인증·소유자·입력 정책, AI 생성 경로는 Fixed/Dynamic·입출력 Guard·Privacy를 적용합니다. 비용 검사는 각 유료 호출 앞에 끼워집니다.'),
P('비용6단계 | 호출 전부터 종료까지','Guardrail 사용 비용도 공통 예산 장부에 포함합니다.',cost,grid=True,note='소스 기본: 월 $2 / 일 $0.50 / 세션 $0.12, 생성 상한 2,048. 촬영 전용: 생성256 / 세션$0.003. 모의 usage와 실제 청구를 구분합니다.'),
P('화면13: 지표를 운영 언어로','“무슨 수치인가?”에서 “무엇을 확인할까?”로.',[
('Dynamic Guard 1 | 요청 단위','차단행동 반복 시도 / 위험행동 반복 / 외부전송 위험 재시도를 색으로 구분. 결합 발생 건수와 작은 시계열을 함께 봅니다.'),('Dynamic Guard 2 | 대화 단위','사유별 누적 점수·세션 차단 상태를 별도로 표시. 두 점수를 더하지 않으며, 누적값은 서버 메모리 기준입니다.'),('오해하지 않는 판정','요청 80점은 서버 위험 판정의 차단 권고입니다. 실제 도구 실행 차단·데이터 유출 확정이 아니며 Trace와 Incident를 검토합니다.'),('관측 연결','앱 MeterRegistry → C16 /actuator/prometheus → 기존 Prometheus 수집 → guardtimeseries → 화면13. C17 위험 이력으로 상세를 연결합니다.')],image='output/presentation-2026-10-25/screen13.png',note='그래프는 실제 Prometheus가 수집한 시계열입니다. 오늘 시계열의 발생 원천은 명시된 더미·모의 요청입니다.'),
P('누구나 이해할 수 있게, 권한은 분명하게','화면을 쉽게 만드는 일과 데이터를 안전하게 나누는 일을 함께 설계했습니다.',[
('USER','본인 문의만 조회. 다른 의뢰인의 상세·검색·파일은403, 내부 메모는 화면 숨김에 그치지 않고 JSON에서도 제외.'),('LAWYER','배정된 문의만 열람·저장. 배정 회수 후403이며 기존 내부 편집 화면도 제거됩니다.'),('ADMIN','전체 현황·배정·내부 업무 확인. USER/LAWYER 계정으로 운영자 로그인하면 거절합니다.'),('모바일','화면01~08을390px 브라우저에서 검수. 화면 이동·입력·충돌 안내를 확인했으며 실제 휴대폰/LAN 접속 검수는 별도입니다.')],note='홍보 문구: 고객은 쉽게 설명하고, 변호사는 빠르게 파악하고, 관리자는 위험과 비용을 한눈에 봅니다.'),
P('작동을 넘어, 멈춰야 할 때 멈추는가','Ground Rule: 정상 · 실패 · 차단 · 사유 · 관측까지 한 묶음으로 검증합니다.',[
('이번 검수','Q01 역할 교차 시나리오14개 통과. 화면01~08 PC·390px 및 충돌/재로그인 레이아웃10개. 보안·비용·검색95개·프런트201개·시연 필수15개 통과.'),('실제로 고친 문제','로그아웃·계정 변경 후 남던 검색 필터를 초기화했습니다. 새로고침으로 메모리 로그인이 풀리는 이유를 명시했습니다.'),('현재 가능한 시연','더미 문의10건, 저장된 검색 실행/설명, 담당자 검토, 위험 이력과 실제 수집 그래프, 정상·차단·오류·비용 모의 검증.'),('아직 실측이 아닌 것','실제 OpenAI 연결·검색 품질·청구 확인은 사용자와 Claude가 진행합니다. 현재 발표 자료는 모의 검증이며 실제 결과가 확인되면 해당 컷만 교체합니다.')],note='마무리: “AI를 붙이는 데서 끝내지 않고, 서비스가 안전하게 운영되는 방식까지 설계했습니다.”')]

product.insert(6,P('관리자가 바로 읽는 AI 위험 요약','요청별 위험과 대화 누적을 나란히, 조치 후보와 근거를 함께.',image='output/presentation-2026-10-25/screen13.png',imageOnly=True,note='시연 더미·모의 요청 → 실제 앱 Metric → 기존 Prometheus 수집 → 실제 화면13. 위험 후보를 보여주며 유출 확정이나 자동 도구 실행 차단을 뜻하지 않습니다.'))

shots=[
('00:00~00:30','PM 오프닝','표지 → 고객/사무소 문제','“법률 문제를 처음 겪는 고객과 초기 응대가 바쁜 사무소를 연결합니다. 상담 전에 정보를 정리하고, 운영 중 위험과 비용까지 확인하는 서비스입니다.”','상담은 더 명확하게. AI 운영은 더 안심되게.'),
('00:30~01:25','USER 접수','화면01 → 02 → 03 → 04','“사건을 짧게 입력하면 규칙과 저장된 절차 정보로 먼저 안내합니다. 초반부터 매번 LLM에 원문을 보내지 않습니다.”','짧은 입력 → 준비할 자료가 보이는 안내'),
('01:25~02:20','검색 흐름','화면05의 시연 검색 요청1번','“조건으로 검색문을 만들고 변협 도메인 대상과 일반 웹 결과를 함께 모읍니다. 출처와 설명을 구분하고 같은 요청 번호로 다시 확인합니다.”','원문 근거를 확인할 수 있는 공개 정보 탐색'),
('02:20~03:20','LAWYER 업무','화면06 목록 → 08 상세 → 07 메모','“담당 변호사는 배정 문의만 검토합니다. 내부 메모를 작성하고 상태를 바꾸며, 동시 수정은 충돌을 알린 뒤 입력을 보존합니다.”','담당자에게 필요한 정보만, 내부 검토는 분리'),
('03:20~04:00','필수 실패 장면','비로그인401 / 다른 USER403 / 오래된 저장409','“로그인이 없으면 저장을 멈추고, 다른 의뢰인의 자료는 차단합니다. 오래된 화면이 최신 내용을 덮어쓰지 않게 합니다.”','정상 동작뿐 아니라 안전한 중단까지'),
('04:00~04:40','보안·비용','보안/비용 카드 → 화면11 C16','“생성 전 위험 검사를 하고, 각 유료 단계의 예약·정산·잔여 예산을 확인합니다. 출력이 차단돼도 이미 사용한 비용은 남습니다.”','Guardrail까지 포함한 비용 통제'),
('04:40~05:40','대표 화면13','09 요약 → 10 관심 분야 → 12 Incident → 13 확대','“관리자가 지표 이름을 외울 필요는 없습니다. 반복 시도와 외부 접근 위험을 조합해 보여주고, 요청 점수와 대화 누적을 분리해 확인합니다.”','지표를 나열하지 않고, 대응할 위험을 보여줍니다.'),
('05:40~06:10','모바일','390px 화면02/03/07 컷','“같은 흐름을 좁은 화면에서도 사용할 수 있도록 검수했습니다. 이 장면은 실제 휴대폰 접속이 아닌390px 브라우저 검수입니다.”','PC에서 모바일 화면까지 이어지는 접수 흐름'),
('06:10~06:30','종료','PM 검증 요약 카드','“서비스 흐름, 권한, AI 보안, 비용, 관측을 연결했습니다. 다음 단계는 실제 공급자 사용량과 검색 품질을 같은 기준으로 대조하는 것입니다.”','설계에서 운영 검증까지 연결하는 Technical PM')]
filming=[
P('6분30초 시연의 한 문장','“고객의 첫 설명을 상담 준비로 바꾸고, 위험과 비용을 관리자의 언어로 보여줍니다.”',[
('녹화 순서','전체를 한 번에 찍기보다 USER / 검색 / 변호사 / 실패 / 관리자 / 모바일의6클립으로 촬영합니다.'),('검증 표기','더미·모의 공급자 표시는 유지합니다. 기존 저장 결과 조회와 지금 새로 실행한 검색을 구분합니다.'),('강조 장면','화면13에 가장 긴 시간을 배정합니다. 화면09~12는 각 역할을 짧게 보여주고13으로 연결합니다.'),('편집 기준','실제 클릭·응답·사유를 남깁니다. 로딩은 줄일 수 있지만 실패를 정상 응답처럼 붙이거나 모의 응답을 실호출처럼 편집하지 않습니다.')]),
P('컷1~3 | 문제와 고객 여정','입력 예시: “돈을 빌려줬는데 돌려받지 못했어요. 계약서와 이체 내역이 있어요.”',[(f'{s[0]} | {s[1]}',f'화면: {s[2]} / 내레이션: {s[3]} / 자막: {s[4]}') for s in shots[:3]],note='USER user로 로그인. 검색 결과의 [모의] 제목을 지우지 않습니다. 네이버 실키 준비 전에는 실제 검색 적중 시연으로 말하지 않습니다.'),
P('컷4~5 | 담당자와 필수 실패','실패는 서비스의 약점 대신 안전한 경계를 설명하는 장면으로 구성합니다.',[
(f'{s[0]} | {s[1]}',f'화면: {s[2]} / 내레이션: {s[3]} / 자막: {s[4]}') for s in shots[3:5]],
note='401: 비로그인 저장.403: user2가 문의1번 조회.409: 같은 문의를 두 탭에서 연 뒤 먼저 다른 탭에서 저장. 입력 보존 후 “확인 후 편집 계속”까지 촬영.'),
P('컷6~7 | 위험과 비용이 보이는 운영','화면13을 대표 상품 장면으로 배치합니다.',[(f'{s[0]} | {s[1]}',f'화면: {s[2]} / 내레이션: {s[3]} / 자막: {s[4]}') for s in shots[5:7]],note='C16 실사용·추정·예약은 모의 usage를 앱이 실제 계산한 값입니다. 공급자 실제 청구가 아닙니다. 요청80점 권고와 누적100점 제한은 합산하지 않습니다.'),
P('컷8~9 | 모바일과 PM 마무리','소스·화면·검수 증거가 같은 이야기를 하도록 마무리합니다.',[(f'{s[0]} | {s[1]}',f'화면: {s[2]} / 내레이션: {s[3]} / 자막: {s[4]}') for s in shots[7:]],note='마케팅 보조 문구: “더 나은 상담은 더 명확한 첫 질문에서 시작됩니다.” / “위험은 차단하고, 이유는 남기고, 비용은 확인합니다.”'),
P('오늘은 OBS + Clipchamp를 추천합니다','ChatGPT는 대본·자막·컷 구성 검토에 활용합니다.',[
('녹화 | OBS','1080p·30fps, 브라우저 창 캡처, 마이크 음량 확인. 설정 마법사 후20초 시험 녹화로 글자·음성·개인 알림을 확인합니다. 설치가 없다면 Clipchamp 화면 녹화로 바로 시작해도 됩니다.'),('편집 | Clipchamp','여섯 클립을 순서대로 배치 → 대기/실수 부분 자르기 → 한국어 자동 자막 교정 → 핵심 문구만 강조 →1080p MP4로 내보내기.'),('ChatGPT 활용','대본을30% 줄이기, 자막을 두 줄 이내로 다듬기, 장면별 핵심 메시지·발표 Q&A 정리. 실제 화면 캡처·타임라인 편집·최종 내보내기는 녹화/편집 도구로 수행합니다.'),('민감정보 없는 촬영','OpenAI 키 입력·설정 파일·프로젝트 잔액/계정 페이지는 녹화하지 않습니다. 가상 사례와 공개 시연 계정만 사용하고 모의 배너를 유지합니다.')],note='공식 자료: obsproject.com/kb/quick-start-guide / clipchamp.com/en/blog/clipchamps-auto-captioning-feature/ . 실제 목소리 녹음 뒤 SRT 시간을 다시 맞추세요.')]

record_lines=[(f'문의 {r["id"]} | {r["title"]}',f'소유자 {r["owner"]}'+(f' / 검색 요청 {r["searchRequestId"]}: {r["searchStatus"]}' if 'searchRequestId'in r else '')) for r in demo.get('records',[])]
validation=[
P('촬영용 자료와 원래 자료를 분리합니다','현재18080은 시연 전용 파일 H2이며 원래 DB 파일은 보존했습니다.',record_lines,compact=True,note='자료는 모두 가상. 화면 왼쪽 아래 더미·모의 표시 유지. 초기 상태 혼합·메모·위험 이력은 실제 업무 API로 생성했고 검색 요청1은 모의 공급자로 COMPLETED 확인.'),
P('필수 오류 검증은 여섯 장면이면 충분합니다','상태 코드와 사용자가 다음에 할 행동을 같이 보여줍니다.',[
('400 | 잘못된 입력','필수값 누락·100자 초과·잘못된 코드. 요청은 저장되지 않으며 필드를 수정합니다.'),('401 | 로그인 없음·만료','보호 경로에서 중단. 비밀번호 실패 후 입력 비움, 만료 후 내부 화면 제거, 새로고침 시 재로그인 안내.'),('403·404 | 자료 경계','다른 USER·미배정 LAWYER의 문의/파일은403. 본인 문의에 다른 파일ID를 끼우면404.'),('409 | 같은 키·다른 본문 / 버전 충돌','자동 덮어쓰기 대신 최신 상태 확인. 메모·선택 항목을 보존하고 명시적으로 편집을 재개합니다.'),('출력 차단·공급자 오류','생성 원문을 그대로 보내지 않고 안전 응답. 이미 수행한 단계는 정산하며 실패했다고 비용을0으로 만들지 않습니다.'),('예산 차단','테스트 세션 예산을 작게 둔 모의 호출로 재현. 실제 과금을 반복해 한도를 태우는 방식으로 시연하지 않습니다.')],grid=True),
P('보안과 비용 검증 결과','정상 성공 외에 실패·차단·reasonCode·관측까지 확인했습니다.',[
('Q01 | 이번 실제 Chrome 검수','14개 시나리오 통과. PC·390px 레이아웃10개, 직접 HTTP35개 + 브라우저 API 응답98개. 둘은 서로 다른 집계이므로 테스트 수로 합산하지 않습니다.'),('집중 자동 검사','보안·비용·검색95개, 실패/오류/skip0. 원격 네트워크는 로컬 모의 서버이며 OpenAI·네이버 실청구 검증이 아닙니다.'),('더미 공급자 실제 HTTP','필수15개: 잘못된 입력·권한·409·위험 URL·음수 순서·내부 메모 제외, 출력 차단 후 비용 유지·usage 미확인 추정 정산·마스킹·예산 차단·예약0·실제 시계열 확인. usage 값은 모의이며 앱 정산 로직은 실제 실행했습니다.'),('과거 전체 회귀와 구분','2026-10-23 독립 전체260개 통과는 이전 증거입니다. 이번95개에 포함·중복될 수 있으므로355개로 합산하지 않습니다.')],note='증거: output/q01-cross-role-2026-10-25/verification.json 및 security-cost-junit / output/presentation-2026-10-25/demo-data.json'),
P('실제 LLM·검색 연결 전 남은 문턱','실제 연결은 사용자·Claude 담당. 모의 시연은18080/5176/9091로 분리해8080/5175를 비워 두었습니다.',[
('Claude 담당 경계','OpenAI 키·모델·단가·실제 공급자 설정은 Codex가 수정하지 않습니다. 별도 시연 DB와 모의 공급자로 화면·오류·보안·비용 검수를 진행합니다.'),('직접 연결1회','모델 목록과 실제 요금을 확인하고 짧은 요청1건만 실행. 반환 모델·usage·비용과 공급자 사용량을 대조합니다. 직접 API 성공은 Guard 포함 앱 성공과 별개입니다.'),('실제 앱 Guard','실제 NeMo 입력/출력 Guard의 연결·모델·usage/추정 기준·출력 상한을 따로 확인합니다. 모의 PASSED를 실제 Guard 성공으로 말하지 않습니다.'),('네이버 키와 검색 품질','현재 미설정. 변협 대상/일반 웹을 함께 조회한 실제 출처·지역·분야 관련성을 확인한 뒤1건에서3건으로 확대합니다.')],note='큰 수정 없이: 정확한 지역·법률 분야·분쟁 유형을 저장하고, 기존 변협+일반 병합·중복 제거·원문 링크·부분 실패 경고를 활용합니다. 가짜 프로필/순위는 추가하지 않습니다.'),
P('촬영 직전 체크리스트','새 기능을 늘리기보다 안정적인 실행본과 증거를 고정합니다.',[
('실행','Node scripts/Start-PresentationDemo.cjs : 비어 있는18080/5176/18083만 사용. 실제 키를 읽지 않는 모의 공급자와 별도 시연 DB를 시작합니다. 이미 실행 중이면 다시 띄우지 않습니다.'),('화면','USER user / LAWYER lawyer / ADMIN admin. 로그인 화면에서 역할을 바꾸고 관리자 지표 연결은 별도 인증합니다. 자막·녹화에는 비밀번호 입력을 넣지 않습니다.'),('자료','문의1·검색 요청1을 메인 사례로 사용. 다른 USER의 문의10으로 권한 경계를 설명합니다. 기존 저장 결과 조회는 LLM을 새로 호출하지 않습니다.'),('모바일·녹화','390px 브라우저 장면은 검수 완료. 실제 휴대폰은 같은 LAN 연결·허용된 서버 바인딩·방화벽을 별도로 확인해야 하며 localhost로 휴대폰에서 접속할 수 없습니다.'),('재시작 한계','문의 DB 파일은 유지되지만 Dynamic 세션·비용 메모리 장부·누적 Metric은 재시작 때 초기화됩니다. 재촬영 전 seed/지표를 다시 준비하고 실제 비용 영속 원장이라고 설명하지 않습니다.')],note='현재 주소: http://127.0.0.1:5176/intake.html#/login / operator.html#/observability . 정상 조회와 모의/실제 경계를 확인한 뒤 녹화를 시작합니다.')]

def para(c,text,x,y,w,size=18,color=TEXT,bold=False):
    style=ParagraphStyle('p',fontName='KoreanBold' if bold else 'Korean',fontSize=size,leading=size*1.52,textColor=HexColor(color),wordWrap='CJK',spaceAfter=0)
    p=Paragraph(escape(str(text)).replace('\n','<br/>'),style)
    _,height=p.wrap(w,1000);p.drawOn(c,x,y-height);return height

def draw_page(c,page,number,total,series):
    c.setFillColor(HexColor(BG));c.rect(0,0,W,H,fill=1,stroke=0)
    c.setFillColor(HexColor(CYAN));c.rect(36,H-40,4,18,fill=1,stroke=0)
    para(c,series.upper(),50,H-24,W-100,11,CYAN,True)
    title_size=31 if '\n' not in page['title'] else 34
    height=para(c,page['title'],42,H-72,W-84,title_size,TEXT,True)
    y=H-80-height
    lead_height=para(c,page['lead'],44,y,W-88,17,CYAN); y-=lead_height+22
    bottom=89 if page.get('note') else 62
    items=page['items']; image=ROOT/page['image'] if page.get('image') else None
    if image and image.exists() and page.get('imageOnly'):
        iw,ih=Image.open(image).size
        scale=min((W-84)/iw,(y-bottom)/ih)
        dw,dh=iw*scale,ih*scale
        c.drawImage(str(image),(W-dw)/2,y-dh,width=dw,height=dh,preserveAspectRatio=True)
    elif image and image.exists():
        # Keep complete screenshot readable as its own broad inset; explanations remain concise.
        iw,ih=Image.open(image).size; image_w=400; image_h=image_w*ih/iw
        if image_h>310:image_h=310;image_w=image_h*iw/ih
        c.drawImage(str(image),W-44-image_w,y-image_h,width=image_w,height=image_h,preserveAspectRatio=True)
        item_w=W-118-image_w; item_h=(y-bottom-12)/len(items)
        for i,(title,body)in enumerate(items):
            top=y-i*item_h;para(c,title,46,top,item_w,16,BLUE,True);h=para(c,body,46,top-30,item_w,14,MUTED)
            assert h+32<item_h,(number,title,h,item_h)
    elif page.get('grid'):
        gap=14;card_w=(W-84-gap)/2;card_h=(y-bottom-2*gap)/3
        for i,(title,body)in enumerate(items):
            x=42+(i%2)*(card_w+gap);top=y-(i//2)*(card_h+gap)
            c.setFillColor(HexColor(PANEL));c.roundRect(x,top-card_h,card_w,card_h,10,fill=1,stroke=0)
            th=para(c,title,x+17,top-14,card_w-34,18,BLUE if i<2 else GREEN if i<4 else RED,True)
            bh=para(c,body,x+17,top-22-th,card_w-34,15,MUTED)
            assert th+bh+32<=card_h,(number,title,th,bh,card_h)
    else:
        compact=page.get('compact');gap=9 if compact else 13
        card_h=(y-bottom-(len(items)-1)*gap)/max(len(items),1)
        for i,(title,body)in enumerate(items):
            top=y-i*(card_h+gap)
            c.setFillColor(HexColor(PANEL));c.roundRect(42,top-card_h,W-84,card_h,9,fill=1,stroke=0)
            if compact:
                para(c,title,58,top-9,430,15,BLUE,True);para(c,body,508,top-9,450,14,MUTED)
            else:
                tight=card_h<110
                th=para(c,title,59,top-10 if tight else top-12,W-118,16 if tight else 18,BLUE,True)
                bh=para(c,body,59,top-16-th if tight else top-18-th,W-118,13.5 if tight else 16,MUTED)
                assert th+bh+(24 if tight else 30)<=card_h,(number,title,th,bh,card_h)
    if page.get('note'):
        note_h=para(c,page['note'],44,75,W-88,11,MUTED)
        assert note_h<52,(number,'footnote too long',note_h)
    c.setStrokeColor(HexColor(LINE));c.line(42,35,W-42,35)
    para(c,'LEGAL AI INTAKE  /  2026-10-25  /  DARK TABLET 4:3',44,26,800,9,MUTED)
    para(c,f'{number:02d} / {total:02d}',W-100,26,70,9,CYAN,True)
    c.showPage()

def build(name,heading,pages):
    pdf=OUT/(name+'_DARK_TABLET.pdf');c=canvas.Canvas(str(pdf),pagesize=(W,H));c.setTitle(heading);c.setAuthor('Legal AI Intake - Technical PM')
    md=[f'# {heading}','', '2026-10-25 / 발표·녹화용 현행 요약. 원문 Ground Rule 및 현행 코드·검수 결과 기준.','']
    for n,p in enumerate(pages,1):
        draw_page(c,p,n,len(pages),heading)
        md += [f'## {n:02d}. {p["title"].replace(chr(10)," ")}', '',p['lead'],'']
        for title,body in p['items']:md += [f'### {title}',body,'']
        if p.get('note'):md += ['> '+p['note'],'']
    c.save();(DOC/(name+'.md')).write_text('\n'.join(md)+'\n',encoding='utf-8')
    reader=PdfReader(pdf);assert len(reader.pages)==len(pages)
    alltext=''.join(p.extract_text()or''for p in reader.pages);assert '\ufffd'not in alltext
    thumbs=[]
    for i in range(len(reader.pages)):
        target=QA/f'{name}-{i+1:02}.png'
        subprocess.run([shutil.which('pdftoppm'),'-f',str(i+1),'-l',str(i+1),'-singlefile','-png','-scale-to','1024',str(pdf),str(target.with_suffix(''))],check=True,capture_output=True)
        image=Image.open(target).convert('RGB');image.thumbnail((512,384));thumbs.append(image.copy())
    sheet=Image.new('RGB',(1024,((len(thumbs)+1)//2)*414),'#0e1725');d=ImageDraw.Draw(sheet)
    for i,img in enumerate(thumbs):x=(i%2)*512;y=(i//2)*414;sheet.paste(img,(x,y));d.text((x+12,y+388),f'{name} {i+1:02}',fill='white')
    sheet.save(QA/(name+'-contact.png'))
    return {'file':str(pdf),'pages':len(pages),'pageSize':[W,H],'embeddedKorean':True,'rendered':True}

results=[build('01_PM_PRODUCT_SECURITY_COST','PM · 제품 · 보안과 비용',product),build('02_DEMO_RECORDING_PLAYBOOK','시연 대본 · 녹화와 편집',filming),build('03_VALIDATION_RUNBOOK','더미 자료 · 검증과 실행',validation)]
(QA/'verification.json').write_text(json.dumps({'date':'2026-10-25','pdfs':results,'q01Passed':review['passed'],'externalPaidCalls':0},ensure_ascii=False,indent=2),encoding='utf-8')
# A timing draft, not subtitles aligned to a finished recording.
srt=[]
for i,s in enumerate(shots,1):
    start,end=s[0].split('~')
    stamp=lambda v:f'00:{v},000'
    srt += [str(i),f'{stamp(start)} --> {stamp(end)}',s[4],'']
(DOC/'DEMO_CAPTIONS_TIMING_DRAFT.srt').write_text('\n'.join(srt),encoding='utf-8-sig')
(DOC/'README.md').write_text('''# 발표·촬영 필수 묶음\n\n2026-10-25. 예전 학습용 전체 API 문서를 모두 들고 발표하지 않고 아래 세 개만 사용합니다.\n\n1. `01_PM_PRODUCT_SECURITY_COST.md` / PDF9쪽: PM 문제 정의·제품 여정·보안6·비용6·대표 화면13·모바일·검증 범위.\n2. `02_DEMO_RECORDING_PLAYBOOK.md` / PDF6쪽:6분30초 대본·마케팅 자막·필수 실패 컷·OBS/Clipchamp 편집.\n3. `03_VALIDATION_RUNBOOK.md` / PDF5쪽: 더미ID·필수 오류·검증 결과·실제 LLM 연결 전제·촬영 체크.\n\nPDF는 `output/pdf/`의 같은 이름 `_DARK_TABLET.pdf`이며4:3 가로 태블릿용입니다. `DEMO_CAPTIONS_TIMING_DRAFT.srt`는 촬영 전 타이밍 초안이므로 실제 녹음 후 조정합니다.\n\n근거: `GROUND_RULE.md`, `docs/DB_DEPLOYMENT_REVIEW_2026-10-23.md`, 현행 API/화면 매핑, Q01 최신 실행 및95개 집중 검사, 촬영용 모의 공급자 실제 HTTP 결과. 비밀키와 원문 DB 백업은 포함하지 않습니다.\n\n권장 도구 공식 근거: https://obsproject.com/kb/quick-start-guide / https://clipchamp.com/en/blog/clipchamps-auto-captioning-feature/ / https://clipchamp.com/en/blog/screen-and-camera-record/ . ChatGPT는 대본/자막/편집 지시서 작성에 활용하고 실제 서비스 화면 녹화와 타임라인 편집은 전용 도구에서 합니다.\n''',encoding='utf-8')
print(json.dumps(results,ensure_ascii=False))
