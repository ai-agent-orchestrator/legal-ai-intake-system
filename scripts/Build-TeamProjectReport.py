from pathlib import Path
from io import BytesIO
from hashlib import sha256
from xml.sax.saxutils import escape
import re,json,math,subprocess
from PIL import Image,ImageDraw,ImageFont
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader
from pypdf import PdfReader,PdfWriter,Transformation
from pypdf.annotations import Link
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/team-project-report-2026-10-26/revised-v3';OUT.mkdir(parents=True,exist_ok=True)
QA=OUT/'qa';QA.mkdir(exist_ok=True)
W,H=1280,960
BG,PANEL,LINE,TEXT,MUTED,BLUE,CYAN,GREEN,RED,GOLD='#0e1725','#162438','#2b405b','#e8eff7','#adbed2','#68a8ff','#6bd6e8','#70dfb2','#ff9eaa','#eac778'
for n,f in [('KR','malgun.ttf'),('KRB','malgunbd.ttf')]:pdfmetrics.registerFont(TTFont(n,'C:/Windows/Fonts/'+f))
CUSTOM=OUT/'report-authored-pages.pdf';c=canvas.Canvas(str(CUSTOM),pagesize=(W,H))
parts=[];count=0;opened=False;sources={};bounds=[];md=[]
sections=[('01','PM 포지셔닝 · 쉬운 운영 관리'),('02','프로젝트 개요 · 제품 소개'),('03','수행절차 · Ground Rule · 일정'),('04','수행결과 · 시스템 구조·ERD'),('05','Screen-API-Controller 매핑'),('06','핵심 세부 백엔드 흐름'),('07','화면정의서 · 구현01~13'),('08','DDD · Actor-Command-Policy-Code'),('09','보안 · 역할·데이터·반복 위험'),('10','비용관리 · 실험·OpenAI 대조'),('11','Error Stop Flow · 실패 경계'),('12','마케팅 Board · 집계·검증'),('13','시연·수행결과·한계·자료 색인')];names=dict(sections)
def src(path):
 p=Path(path);p=p if p.is_absolute() else ROOT/p;assert p.exists(),p
 sources[str(p)]=dict(path=str(p),bytes=p.stat().st_size,sha256=sha256(p.read_bytes()).hexdigest());return p
def clean(s):
 s=re.sub(r'\[([^\]]+)\]\([^)]*\)',r'\1',str(s))
 return s.replace('**','').replace('`','').replace('<br/>','\n').replace('<br>','\n').replace('\\|','|').replace('{{baseUrl}}','')
def paragraph(s,w,size=15,color=TEXT,bold=False):
 st=ParagraphStyle('p',fontName='KRB' if bold else 'KR',fontSize=size,leading=size*1.42,textColor=HexColor(color),wordWrap='CJK',splitLongWords=True)
 p=Paragraph(escape(clean(s)).replace('\n','<br/>'),st);_,h=p.wrap(w,4000);return p,h
def text(s,x,y,w,size=15,color=TEXT,bold=False):
 p,h=paragraph(s,w,size,color,bold);assert x>=0 and x+w<=W+1 and y+h<=900,(str(s)[:80],y,h)
 p.drawOn(c,x,H-y-h);bounds.append(dict(page=count,x=x,y=y,width=w,height=h));return h
def end():
 global opened
 if opened:c.showPage();opened=False
def begin(sec,title,lead='',source=''):
 global count,opened
 end();count+=1;opened=True;parts.append(dict(file=str(CUSTOM),page=count-1,section=sec,title=title,origin='authored'))
 c.setFillColor(HexColor(BG));c.rect(0,0,W,H,fill=1,stroke=0)
 text('LEGAL AI INTAKE / TEAM PROJECT REPORT',48,25,1180,12,CYAN,True)
 text(title,48,65,1184,30,TEXT,True)
 if lead:text(lead,50,117,1180,14,MUTED)
 if source:text('근거: '+source,50,881,1174,9,MUTED)
 md.append('\n## '+title+'\n\n'+lead+'\n')
def box(x,y,w,h,title,body,color=CYAN,size=15):
 c.setFillColor(HexColor(PANEL));c.setStrokeColor(HexColor(color));c.roundRect(x,H-y-h,w,h,10,fill=1,stroke=1)
 th=text(title,x+15,y+12,w-30,size+2,color,True);bh=text(body,x+15,y+23+th,w-30,size)
 assert 23+th+bh<h-8,(title,h,th,bh)
def arrow(x,y,xx,yy,dashed=False):
 c.setStrokeColor(HexColor(CYAN));c.setLineWidth(1.6)
 c.setDash(5,4) if dashed else c.setDash();c.line(x,H-y,xx,H-yy);c.setDash();a=math.atan2(yy-y,xx-x)
 for d in [-.55,.55]:c.line(xx,H-yy,xx-8*math.cos(a+d),H-(yy-8*math.sin(a+d)))
def imported(sec,path,start=0,stop=None):
 end();p=src(path);r=PdfReader(p);stop=len(r.pages) if stop is None else stop
 for n in range(start,stop):parts.append(dict(file=str(p),page=n,section=sec,title=p.stem+' / '+str(n+1),origin='existing-pdf'))
 md.append('\n## '+names[sec]+f'\n\n既存PDF `{p.relative_to(ROOT)}` {start+1}~{stop}쪽 통합.\n')
def table(sec,title,lead,headers,rows,widths,size=12,source=''):
 assert sum(widths)==1184
 seq=0;y=180
 def start():
  nonlocal seq,y
  seq+=1;begin(sec,title+(' · 계속' if seq>1 else ''),lead,source);y=180;xx=48
  hh=max(paragraph(v,w-18,size+1)[1] for v,w in zip(headers,widths))+20
  c.setFillColor(HexColor('#203b52'));c.rect(48,H-y-hh,1184,hh,fill=1,stroke=0)
  for v,w in zip(headers,widths):text(v,xx+9,y+10,w-18,size+1,CYAN,True);xx+=w
  y+=hh
 start()
 for i,row in enumerate(rows):
  assert len(row)==len(headers),(title,row)
  hh=max(paragraph(v,w-18,size)[1] for v,w in zip(row,widths))+23;assert hh<620,(title,hh)
  if y+hh>852:start()
  c.setFillColor(HexColor(PANEL if i%2==0 else BG));c.rect(48,H-y-hh,1184,hh,fill=1,stroke=0)
  c.setStrokeColor(HexColor(LINE));c.line(48,H-y-hh,1232,H-y-hh);xx=48
  for v,w in zip(row,widths):text(v,xx+9,y+11,w-18,size);xx+=w
  y+=hh
 md.append('| '+' | '.join(headers)+' |\n| '+' | '.join('---' for _ in headers)+' |\n'+'\n'.join('| '+' | '.join(clean(v).replace('\n',' / ').replace('|','\\|') for v in row)+' |' for row in rows)+'\n')
def picture(path,x,y,w,h):
 p=src(path);im=Image.open(p);s=min(w/im.width,h/im.height);ww,hh=im.width*s,im.height*s
 c.drawImage(ImageReader(im),x+(w-ww)/2,H-y-hh,ww,hh,mask='auto')
def imagepage(sec,title,lead,path,details=()):
 begin(sec,title,lead,str(Path(path).relative_to(ROOT)));picture(path,48,175,1184,574);y=765
 for h,b in details:y+=text(h+'  '+b,50,y,1180,13,BLUE if h=='API' else TEXT)+9
 assert y<=874,(title,y)
 md.append(f'\n화면/증거: `{Path(path).relative_to(ROOT)}`\n'+'\n'.join('- '+h+': '+b for h,b in details)+'\n')
def tables(path):
 t=[];cur=[]
 for line in src(path).read_text(encoding='utf-8').splitlines()+['']:
  if line.startswith('|'):
   row=[s.strip() for s in re.split(r'(?<!\\)\|',line.strip()[1:-1])]
   if all(re.match(r'^:?-+:?$',s.replace(' ','')) for s in row):continue
   cur.append(row)
  elif cur:t.append(cur);cur=[]
 return t
P=ROOT/'docs/presentation-pdfs'
begin('cover','팀 프로젝트 결과보고서','Security-Aware Legal AI Intake System')
text('보안·비용 통제\n쉬운 운영 관리\nLegal AI Chatbot System',62,245,1120,47,CYAN,True)
box(56,560,1168,200,'Technical PM · 선택형 Intake · 보안·비용·관측','제품 소개 / 개발 방법 / ERD / API·Controller / 핵심 흐름 / 화면01~13 / DDD\n보안·비용·오류 중단·마케팅 실험을 하나의 제출 자료로 통합했습니다.',GREEN,19)
text('제출 기준 2026-10-26 · 기존 개발·실험 산출물 통합본 · 4:3 DARK TABLET',60,812,1150,16,MUTED)
begin('contents','목차','각 장을 클릭해 이동합니다. 화면13개와 실제·모의 실험 증거를 함께 연결했습니다.');end()
imported('01',P/'PM_SECURITY_CORE_DARK_TABLET.pdf')
begin('02','보안·비용 통제·쉬운 운영 관리','Legal AI Chatbot System · 오프닝 문구형 시안')
c.setFillColor(HexColor('#070b13'));c.setStrokeColor(HexColor(LINE));c.roundRect(48,170,1184,610,18,fill=1,stroke=1)
text('“AI 기능을 붙이는 건 쉽습니다.',82,240,1100,39,TEXT)
text('어려운 건 실패해도 멈추고,',82,335,1100,39,TEXT)
text('돈이 새지 않는 AI 백엔드입니다.',82,430,1100,39,GREEN,True)
text('저는 그 본질에 집중했습니다.”',82,555,1100,39,CYAN,True)
text('실패 경계 · 유료 호출 · 비용 장부 · 직관적인 운영 관측',82,744,1100,18,MUTED)
md.append('\n> AI 기능을 붙이는 건 쉽습니다. 어려운 건 실패해도 멈추고, 돈이 새지 않는 AI 백엔드입니다. 저는 그 본질에 집중했습니다.\n')
imported('02',P/'PRODUCT_INTRODUCTION_DARK_TABLET.pdf',0,8)
src(ROOT/'GROUND_RULE.md');src(ROOT/'docs/PROJECT_14_DAY_SCHEDULE_2026-10-26.md')
begin('03','수행절차 · AI 초안에서 검증 가능한 결과까지','Ground Rule의 개발·모듈 완료·실패 중단 원칙을 적용했습니다.','GROUND_RULE.md / PROJECT_14_DAY_SCHEDULE_2026-10-26.md')
steps=[('01 문제·범위 정의','시장 문제 → 사용자 UX\n선택형 intake·MVP 범위 확정'),('02 AI 보조 초안','설계·Controller·DTO·Service 초안\n생성된 코드를 완료로 간주하지 않음'),('03 정상·실패 실험','Windows PowerShell·자동 검사·브라우저\n응답·ID·DB·후속 중단 여부 확인'),('04 정책·코드 매핑','Actor → Command → Policy → Code\n사유 코드·Metric·Read Model 연결'),('05 수정·재실험','오류·중복·권한·비용 분기 보완\n같은 조건으로 결과 재대조'),('06 산출물·운영 설명','ERD·화면·API·증거·시연 갱신\n실제·모의·미검증 구분')]
for j,(h,b) in enumerate(steps):box(48+(j%3)*400,200+(j//3)*270,384,225,h,b,[BLUE,GREEN,CYAN][j%3],17)
text('완료 기준: 코드 + 정상·실패·차단 요청 + 사유 코드 + 로그/지표 + 피드백 + 문서 갱신',50,785,1175,17,GREEN,True)
md.extend('- '+h+': '+b for h,b in steps)
table('03','수행 방법 · Top-Middle-Bottom','계획·구현·자동 검사·브라우저·실공급자 검증을 서로 다른 근거로 관리합니다.',['구분','적용 방법','확인할 결과'],[
['Top · 전체 흐름','사용자 → 보안 → Controller → Service → DB/LLM → 출력 보호 → 관측','제품·Screen-API-Controller·백엔드3개 흐름'],['Middle · 도메인','Actor → Command/Query → Policy → Aggregate 후보 → Read Model','DDD 명령·사실·정책·저장·코드 매핑'],['Bottom · 검증','요청값·반환 ID·상태·DB 행·사유 코드·사용량 대조','동시 재전송·권한 회수·부분 완료·비용·OpenAI'],['Error Stop','오류 이후 생성·저장·외부 호출 중단을 확인','401/400/403/409·차단·fallback·결과 보존'],['완료 판정','모듈 정상·실패·차단 결과와 한계 함께 기록','H2=MySQL 이행 성공, 모의=실측으로 표현하지 않음'],['협업·도구','AI 초안·수정·검증을 보조하고 사람이 정책·범위 결정','실제 저장소·스크립트·보고서로 확인. 팀원별 기여율은 별도 확정 필요']],[170,505,509],15,'GROUND_RULE.md')
s=tables(ROOT/'docs/PROJECT_14_DAY_SCHEDULE_2026-10-26.md')[0]
table('03','수행 일정 · 14일 개발·제출 개정','Day1~5: 설계·구현·보안 연결. 계획과 실제 실행 증거는 구분합니다.',s[0],s[1:5],[60,235,210,400,279],12,'PROJECT_14_DAY_SCHEDULE_2026-10-26.md')
table('03','수행 일정 · 매핑·DDD·화면·검색','Day6~10: Mapping Check·Refactoring → DDD → React/API → Search Routing.',s[0],s[5:9],[60,235,210,400,279],12,'PROJECT_14_DAY_SCHEDULE_2026-10-26.md')
table('03','수행 일정 · PowerShell 검증·시연·발표','Day11~14: Windows PowerShell 실행 기록·체크리스트·시연·발표 품질을 정리합니다.',s[0],s[9:],[60,235,210,400,279],12,'PROJECT_14_DAY_SCHEDULE_2026-10-26.md')
begin('04','시스템 구조 · 선택형 UX에서 운영 관측까지','React·Spring Boot·JPA·외부 공급자·관측의 책임을 분리합니다.','최신 ERD / API·DDD 문서')
box(48,200,355,145,'React · USER / LAWYER','선택형 접수·안내·검색\n배정 문의·상태·내부 메모',BLUE,17)
box(462,200,770,145,'Security → Controller → DTO','JWT·역할·객체 권한 / 입력 검증 / 업무·AI 경로 분기\nC16·guardtimeseries는 별도 ADMIN Basic',CYAN,17);arrow(403,270,462,270)
box(48,425,355,180,'JPA · 업무 기록','문의·자료·검색·메모\n행동 이벤트·위험 이력\nIncident·파일 감사',GREEN,17)
box(462,425,355,180,'Service · 정책·비용','트랜잭션·버전·멱등성\nFixed / Dynamic / Cost\n입출력 Guard / Privacy',GOLD,17)
box(877,425,355,180,'외부 공급자','LLM: 검색문·결과 설명\n네이버: 변협 대상 + 웹\nNeMo: 설정·실측 별도',BLUE,16)
arrow(847,345,639,425);arrow(462,515,403,515);arrow(817,515,877,515)
box(48,690,1184,125,'ADMIN · 운영·마케팅·보안·화면13','업무 DB 집계 + C16 현재 지표 + C17 위험 이력 + guardtimeseries\nMeterRegistry → 기존 Prometheus 수집 → 서버 고정 query_range → React',CYAN,17);arrow(639,605,639,690)
begin('04','ERD 전체 관계도 · 한 페이지 원본','첨부 전체 관계도 원본을 한 장으로 추가했습니다. 최신 보완 필드는 뒤의 ERD 상세 표를 함께 봅니다.','사용자 첨부 전체 ERD / BACKEND_ERD.md')
picture(ROOT/'docs/presentation-pdfs/assets/BACKEND_ERD_FULL_REFERENCE.png',48,168,1184,665)
text('기본 관계 구조 확인용 원본 · 권한·version·멱등 키·P03 실행 필드의 최신 정의는 후속 상세 표에 반영',50,846,1180,12,MUTED)
md.append('\n전체 ERD 원본: docs/presentation-pdfs/assets/BACKEND_ERD_FULL_REFERENCE.png. 최신 스키마 필드 표와 구분해 함께 읽습니다.\n')
begin('04','ERD ① · 핵심 업무8개 테이블','핵심 연결은 DB FK가 아닌 논리 ID 참조입니다. 선은 데이터 연결을 읽기 위한 표시입니다.','BACKEND_ERD.md / H2 DDL')
nodes=[(460,190,360,150,'legal_intake','PK id / owner / assigned_lawyer\nversion · status · idempotency_key'),(48,410,330,140,'intake_document','PK id · logical intake_id\n자료 종류 / 파일 BLOB·메타데이터'),(854,190,378,140,'lawyer_search_condition','PK id · logical intake_id\n분야·분쟁·지역·검색문'),(460,410,360,155,'ai_search_request','PK id · intake_id · condition_id\nexecution_id/lease · stage/status'),(854,410,378,140,'consultation_memo','PK id · logical intake_id\n작성자·본문·request_key/hash'),(48,680,330,125,'intake_document_download','PK id · intake_id · document_id\n요청자·시각'),(460,680,360,125,'search_result_snapshot','PK id · search_request_id\nURL·요약·설명·display_order'),(854,680,378,125,'intake_event','PK event_id · optional intake_id\nVISIT / STARTED / SUMMARY_VIEWED')]
for x,y,w,h,h1,b in nodes:box(x,y,w,h,h1,b,GREEN,14)
for a,b in [((460,265),(213,410)),((820,265),(854,265)),((640,340),(640,410)),((820,300),(1040,410)),((854,315),(820,460)),((213,550),(213,680)),((640,565),(640,680))]:arrow(*a,*b,True)
arrow(820,330,1245,370,True);arrow(1245,370,1245,742,True);arrow(1245,742,1232,742,True)
text('문의1→자료/조건/검색/메모0..N · 검색 요청1→결과0..N · 행동 이벤트의 문의 연결은 선택',50,836,1180,13,MUTED)
schema=src(ROOT/'docs/BACKEND_ERD_SCHEMA_EVIDENCE.sql').read_text(encoding='utf-8')
physical=dict(re.findall(r'CREATE CACHED TABLE "public"\."([^"]+)"\((.*?)\n\);',schema,re.S))
core=['legal_intake','intake_document','lawyer_search_condition','ai_search_request','search_result_snapshot','consultation_memo','intake_document_download','intake_event']
purposes=['접수·소유·담당·상태·버전·접수 지문','선택 자료와 업로드 BLOB·메타데이터','분야·분쟁·지역·선호·검색문','문의·조건·실행 상태/lease·오류','제목·URL·요약·설명·순서','내부 메모·작성자·업무·키','승인된 다운로드 요청 감사','명시적으로 보고된 행동 데이터']
rows=[]
for n,purpose in zip(core,purposes):rows.append([n,purpose,', '.join(re.findall(r'^\s+"([^"]+)"',physical[n],re.M)),'PK event_id' if n=='intake_event' else 'PK id / 업무 연결 logical ID'])
table('04','ERD ② · 핵심 저장 필드','DDL 컬럼명 기준입니다. DB 길이와 API DTO 제한은 다를 수 있습니다.',['테이블','저장 의미','실제 필드','제약'],rows,[190,245,549,200],12,'BACKEND_ERD_SCHEMA_EVIDENCE.sql · H2 데이터 제외 DDL')
others=[n for n in physical if n not in core]
table('04','ERD ③ · Reference·위험·보안·기존 테이블','20개 H2 테이블 중 핵심8개 외 목록. 실습 테이블을 신규 법률 기능으로 계산하지 않습니다.',['테이블','실제 필드','관계·범위'],[[n,', '.join(re.findall(r'^\s+"([^"]+)"',physical[n],re.M)),'procedure_id 실제 FK' if n in ['legal_form_template','legal_procedure_step'] else '기존 실습 범위' if n.startswith('practice_') else '위험·보안·비동기 또는 기존 분석 이력'] for n in others],[230,670,284],11,'BACKEND_ERD_SCHEMA_EVIDENCE.sql')
table('04','ERD ④ · 권한·재전송·버전·영속성','화면13 지표 추가와 후속 문의 권한·동시성 컬럼 보완을 구분합니다.',['항목','저장·보호 구조','확인 범위'],[
['문의 재전송','UNIQUE(owner_username,idempotency_key), request_hash','같은 소유자·키·내용은 같은 문의. 충돌409'],['메모 재전송','UNIQUE(intake_id,request_key), content_hash, created_by','작성자·키·내용 검증. 키 없으면 기본120초 추정'],['상태·메모 동시성','legal_intake.version/updated_at + 같은 문의 행 잠금','expectedVersion 제공 시 충돌409. 생략은 호환 허용'],['담당 권한','assigned_lawyer_username / owner_username','접근·내부 업무 분리. USER JSON 내부 메모 제외'],['Reference FK','step.procedure_id / form.procedure_id','핵심 업무 Long 논리 참조와 구분'],['화면13','MeterRegistry·Prometheus + 기존 위험 이력','신규 업무 테이블 없음. 비용·세션은 메모리'],['DB 이행','H2 DDL / MySQL 수동 이행 SQL 분리','H2 검증은 실제 MySQL 이행 성공이 아님. 백업·복구 별도']],[180,600,404],14,'BACKEND_ERD.md / DB_DEPLOYMENT_REVIEW_2026-10-23.md')
api={};screen_api={str(n):set() for n in range(1,14)}
for tab in tables(ROOT/'docs/SCREEN_API_CONTROLLER_MAPPING.md'):
 for row in tab[1:]:
  if len(row)!=6:continue
  m=re.search(r'\*\*([CRSP]\d{2})\*\*',row[1])
  if not m:continue
  code=m[1];v=[clean(x) for x in row]
  if code not in api:api[code]=v
  for n in re.findall(r'(?<!\d)(0[1-9]|1[0-3])(?!\d)',v[0]):screen_api[str(int(n))].add(code)
extra=[('S01','공통 AI','동기 Chat','POST /api/ai/chat','message100자, sessionId 선택','JWT','AiController.chat → AiService.chat'),('S02','공통 AI','SSE Chat','POST /api/ai/chat/stream','message100자; meta/token/done/blocked/error','JWT','AiController.chatStream → AiStreamingService.stream'),('S03','화면 불필요','입력 단독 검사','POST /api/legal/guardrail/input','message 필수, sessionId 선택','JWT','LegalGuardrailController.checkInput → CostedGuardrailService'),('S04','화면 불필요','출력 단독 검사','POST /api/legal/guardrail/output','response 필수, sessionId 선택','JWT','LegalOutputGuardrailController.checkOutput → CostedGuardrailService'),('S05','화면 불필요','Agent 위험 분석','POST /api/agent/risk/analyze','userInput,toolName,retry/token 수,신호 Boolean','현재 공개 · ADMIN 조회와 구분','AgentRiskDecisionController.analyze → AgentRiskDecisionService'),('P04','03·08 공통','파일 업로드','POST /api/intakes/{intakeId}/documents','multipart file 필수, documentType 선택','JWT + 문의 접근','IntakeDocumentController.upload → IntakeDocumentService'),('P05','08 공통','파일 다운로드','GET /api/intakes/{intakeId}/documents/{documentId}/download','Path intakeId·documentId; Body 없음','JWT + 문의 접근','IntakeDocumentController.download → IntakeDocumentService'),('P06','공통 행동','행동 보고','POST /api/intake-events','eventId,eventType,sessionId; SUMMARY_VIEWED는 intakeId 필수','현재 공개 · 무결성 검사','IntakeEventController.record → IntakeEventService')]
for code,screen,name,url,fields,auth,entry in extra:api[code]=[screen,code+' '+name,url,fields,auth,entry]
assert len(api)==38,(len(api),sorted(api));src(ROOT/'docs/API_STATUS_SPEC.md')
begin('05','Screen → API → Controller → Service','번호 API38개 + 보조 Actuator1개. 공통 API를 화면마다 중복 합산하지 않습니다.','최신 Screen-API-Controller / API_STATUS_SPEC / DDD')
chain=[('화면·Actor','USER01~05 / LAWYER06~08 / ADMIN09~13'),('HTTP·입력','메서드·Path·Query·JSON·multipart·요청 키'),('인증·Controller','JWT / ADMIN Basic / 공개 경로 구분 + DTO 검증'),('Service·Policy','객체 권한·버전·전이·비용·Guard·외부 호출'),('저장·Read Model','JPA·논리 ID / 응답 DTO·파일·집계·Metric')]
for j,(h,b) in enumerate(chain):
 y=180+j*130;box(70,y,1140,104,h,b,[BLUE,CYAN,GREEN,GOLD,BLUE][j],17)
 if j<4:arrow(640,y+104,640,y+130)
rows=[]
for prefix in 'CRSP':
 for code in sorted((k for k in api if k.startswith(prefix)),key=lambda x:int(x[1:])):
  v=api[code];screens=', '.join(str(i).zfill(2) for i in range(1,14) if code in screen_api[str(i)]) or v[0]
  if code=='C21':screens='08 · ADMIN'
  rows.append([screens,v[1],v[2]+'\n'+v[3],v[4],v[5]])
rows.append(['13','보조 Actuator','GET /actuator/guardtimeseries\nParams/Body 없음; application/json','ADMIN Basic','GuardTimeSeriesEndpoint.read → PrometheusTimeSeriesService.read'])
table('05','전체 Screen-API-Controller 매핑','인증·입력·실제 진입점을 연결했습니다. GET은 Path/Query를 사용하며 본문 유무를 구분합니다.',['화면','API·기능','HTTP·입력 계약','인증·대상','Controller → Service'],rows,[90,165,416,180,333],11,'SCREEN_API_CONTROLLER_MAPPING.md / API_STATUS_SPEC.md / 최신 DDD')
table('05','ID 전달 · 생성 응답부터 후속 요청까지','문의 생성이 검색 실행·메모 작성을 자동 호출하지 않습니다.',['이름','받는 위치','전달 위치·기능'],[
['accessToken','C01 응답','JWT 경로 Authorization: Bearer. C16 Basic과 별도'],['intakeId','C03 응답 id / C09 항목 id','C05·C10·C11·C21 Path, C06·P02·P04·P05 문의 Path'],['conditionId','lawyerSearchCondition.id','서버가 C06 요청과 연결. C06 본문 재전달 불필요'],['searchRequestId','C06 응답 id / P02 항목 id','C07·C08·P03 Path. 같은 ID에 상태·결과 연결'],['version','C05 version / C10 version / C11 intakeVersion','C10·C11 expectedVersion. 재조회 후 최신 값 사용'],['요청 키','클라이언트 생성 시도별 UUID','Idempotency-Key. 재전송 시 유지, 새 업무는 새 키'],['incidentId','C18 Incident 항목 id','C19 ack / C20 resolve Path. riskHistoryId와 구분'],['documentId','P04 응답 id / uploadedDocuments.id','P05 Path. 문의와 파일 소속 확인']],[180,360,644],15,'Screen/API·ID 가이드')
imported('06',P/'PRODUCT_INTRODUCTION_DARK_TABLET.pdf',10,13)
specs=[
('01','공통 로그인','USER·LAWYER·ADMIN','계정 입력 → C01 → P01 → 역할 확인','필수값·로그인/프로필 오류·만료·로그아웃','output/q01-cross-role-2026-10-25/screen01-pc.png','기존 앱 검수·개발/모의 환경'),
('02','사건 분류·절차 안내','비로그인 포함','본문100자·유형 → 규칙 분류 → 절차 목록/상세','공백·길이·로딩·빈 결과·오류 복구','output/intake-01-04-browser/02-success.png','기존 규칙 안내 검수·LLM 미호출'),
('03','문의 등록·자료 준비','로그인 저장','사건·자료·희망 단계 → C03 → 문의·조건 저장','저장401·필수값·자료·키·재전송','output/intake-01-04-browser/03-normal.png','기존 앱 검수·더미 사례'),
('04','간단 안내·접수 내용','문의 접근 권한','C04 규칙 안내 + C05 접수 + R01/R02/R06','없는 문의·인증·빈 안내·재조회','output/intake-01-04-browser/04-normal.png','기존 안내 검수·LLM 미호출'),
('05','검색 요청·결과·이력','소유자·배정 LAWYER·ADMIN','조건 → C06 → P03 → C07/P02 조회; C08 수동','미설정·중복 실행·PARTIAL·설명 재시도·URL','output/intake-05-08-browser/05-normal.png','기존 모의 검색 검수·실제 품질과 구분'),
('06','문의 목록','역할별 범위','C09 목록·유형/상태/기간/검색어 → 상세','빈 목록·일자·통신 오류·재조회','output/intake-05-08-browser/06-normal.png','기존 앱 검수·더미 문의'),
('07','상태 변경·상담 메모','배정 LAWYER·ADMIN','C05 version → C10 상태 / C11 메모 → 재조회','409 입력 보존·기존 저장 확인·명시적 재저장','output/intake-05-08-browser/07-normal.png','현행07=상태·메모 · 과거 초안08과 구분'),
('08','문의 상세','소유자·배정 LAWYER·ADMIN','사건·첨부·검색 조건·내부 메모·C21 배정','USER 메모 제외·파일 소속/권한·다운로드','output/intake-05-08-browser/08-normal.png','현행08=상세 · 기존 더미 검수'),
('09','운영 요약','ADMIN JWT','C12 실행·위험·보안·OPEN Incident 요약','로딩·빈 결과·부분/전체 오류·재조회','output/operator-09-12-react-review/09-final-desktop.png','기존 운영자 검수·모의 자료'),
('10','마케팅 집계','ADMIN JWT','C13 문의·검색·고유 문의·현재 준비·분포','집계 분모 구분·이탈률 미측정·별도 시연 탭','output/marketing-demo-2026-10-25/real-desktop.png','더미 DB 실제 C13 집계 · 전환 분석은 별도'),
('11','보안·위험·비용','ADMIN JWT + 별도 Basic','C14 보안·C15 위험·C16 비용/토큰/시간','지표 인증·결측/0 구분·형식·연결 오류','output/operator-09-12-react-review/11-metrics-connected.png','기존 지표 브라우저 검수·모의 자료'),
('12','위험 이력·Incident','ADMIN JWT','C17/C18 선택·상세·필터 → C19/C20','404·종결 역행409·중복 상태·재조회','output/operator-admin-auth/admin-audit.png','기존 관리자 인증·감사 검수'),
('13','AI 위험 요약·Observability','ADMIN JWT + 별도 Basic','C16 현재값 + C17 점수 + guardtimeseries','요청80점 권고/누적100점 등 제한·실제/모의','output/obs13-prometheus/screen13-real-prometheus.png','기존 실제 Prometheus 수집·가상 위험·LLM0')]
table('07','화면정의서 · 실제 구현13개','현행07=상태·메모,08=상세. 화면14는13에 통합해 별도 화면으로 두지 않습니다.',['화면','역할·목적','주요 동작'],[[n+' '+title,role,action] for n,title,role,action,err,img,mode in specs],[235,235,714],14,'화면/API 매핑 / 화면별 검수서')
for n,title,role,action,err,img,mode in specs:
 ids=', '.join(sorted(screen_api[str(int(n))]))
 if n=='03':ids+=', P04'
 if n=='08':ids+=', C21, P05'
 if n=='13':ids+=', 보조 guardtimeseries'
 imagepage('07',f'화면{n} · {title}',role+' | '+mode,ROOT/img,[('동작',action),('API',ids),('검증',err)])
begin('07','모바일 구현 · 입력과 검색의 작은 화면','기존375px 브라우저 검수의 상단 뷰포트입니다. 실제 휴대폰 전체 조작 녹화와 구분합니다.','output/intake-01-04/screen03-mobile.png / output/intake-05-08/screen05-mobile.png')
picture(ROOT/'output/intake-01-04/screen03-mobile.png',170,185,390,620)
picture(ROOT/'output/intake-05-08/screen05-mobile.png',720,185,390,620)
text('문의 등록·선택형 입력',170,819,390,15,CYAN,True)
text('검색 요청·결과 확인',720,819,390,15,CYAN,True)
md.append('\n모바일375px 기존 검수 캡처: 문의 등록·검색 상단 UI. 실제 기기 전체 조작 영상과 구분합니다.\n')
d=tables(ROOT/'docs/BACKEND_DDD_EVENT_STORMING.md')
t=next(t for t in d if t[0][0]=='Bounded Context 초안')
table('08','DDD ① · 책임 경계와 Read Model','단일 Spring Boot 내부의 책임 경계 후보입니다. 완전한 Aggregate Root·마이크로서비스 분리 완료를 뜻하지 않습니다.',t[0],t[1:],[225,425,534],14,'BACKEND_DDD_EVENT_STORMING.md · 2026-10-25 최신')
t=next(t for t in d if t[0][0]=='기능 / 화면')
table('08','DDD ② · Actor와 실제 서버 권한','화면 역할과 서버 인가를 함께 읽습니다. 공개 C02·Reference·S05·P06은 별도 경계입니다.',t[0],t[1:],[290,210,225,230,229],12,'최신 DDD Actor 권한 표')
for i,t in enumerate(t for t in d if len(t[0])==7 and t[0][0]=='Actor'):
 rows=[]
 for actor,command,event,policy,aggregate,readmodel,entry in t[1:]:rows.append([actor,command+'\n→ '+event,policy,aggregate+'\nRead: '+readmodel,entry])
 table('08','DDD ③ · Actor → Command → Event → 코드' if i==0 else 'DDD ④ · 공통·운영·AI Query/Command','Event는 설명용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.',['Actor','Command → Event 후보','Policy','Aggregate / Read Model','실제 진입점'],rows,[145,250,309,240,240],11,'최신 DDD 명령·조회 매핑')
table('08','DDD ⑤ · 설계 용어와 실제 구현 경계','업무 의미·실제 저장 기록·관측 지표를 구분합니다.',['항목','현재 구현','구분'],[
['Command / Query','등록·상태·메모·실행과 읽기 요청 분리','읽을 때마다 새 접수 이벤트가 생기는 것은 아님'],['Domain Event 후보','성공 업무를 과거형 이름으로 설명','이름이 같은 이벤트 객체·발행·구독 코드 없음'],['P06 intake_event','eventId와3개 행동 종류를 명시 보고·저장','React 자동 수집 미연결. 모든 DDD 이벤트 저장소 아님'],['Aggregate 후보','Service·트랜잭션·Repository·잠금으로 보호','완전한 객체 그래프 캡슐화·Event Sourcing 아님'],['Read Model','응답 DTO·조회·DB 집계·지표','별도 CQRS DB·자동 Projection 구축 아님'],['메모 원문·표시','DB 원문 + 반환 패턴 마스킹·권한 필터','DB 원문 전체를 사전 마스킹 저장하는 정책 아님']],[220,465,499],15,'최신 DDD / LegalIntakeRecordService / IntakeEventService')
table('09','보안 검증 · 실험 범위와 결과','전체보안점검 보고서의 별도 worktree·모의 공급자 증거를 재사용합니다.',['검증 축','관측 결과','해석'],[
['G1·G2 인증·입력','401/400, LLM0·접수 증가0','실패 뒤 생성·저장 중단 확인'],['G3 Fixed / G5 Dynamic','비밀 요청 차단·같은 세션 반복 제한','G3 한 실험을 모든 Fixed 유형 실측으로 확대하지 않음'],['G7 Privacy','답변·접수·메모의 검사 원문 패턴0','지원 패턴 기준. 이름·주소 등 모두 보호 보장 아님'],['H8·H9 권한·중복','다른 USER403·회수403 / 접수8→ID1·메모6→1','201 응답 횟수와 DB 저장 건수 구분'],['H3·H4·H10 상태','금지 역행409·같은 상태 no-op','문의·Incident 규칙 각각 검증'],['H11 부분 복구','PARTIAL 결과 ID 유지 → 설명만 재시도','calls4=검색문1+네이버2+설명1. 모두 LLM4회 아님'],['G4·G6 예산·오류','사전 차단LLM0 / 모의500 뒤 다음 요청 정상','초기 estimated+0은 파서 문제. 추정 비용0 증거 아님'],['한계','현재 서버 재병합·재실행·실공급자 검수와 구분','보고서 조립·영상 편집에서 새 유료/보안 호출 없음']],[215,475,494],13,'전체보안점검 보고서 / 08M_SECURITY_REPORT_EDIT.md')
security=[('G1_G2','인증·입력 검증'),('G3','Fixed 비밀 요청 사전 차단'),('G5','Dynamic 같은 세션 반복 제한'),('G7','개인정보 출력 보호'),('H8','사용자·변호사 객체 권한'),('H9','동시 재전송·중복 방지'),('H3_H4_H10','상태 역행·중복 요청'),('H11','부분 결과 보존·설명 복구'),('G4_G6','예산 사전 차단·모의 오류'),('H_REMAINING','저장·대상·집계 의미')]
for key,title in security:imagepage('09','보안 실험 증거 · '+title,'기존 보고서 원본 행 + 관측 결과 + 적용 한계를 함께 읽습니다.',ROOT/f'output/presentation-2026-10-25/eight-minute-security/assets/{key}.png',[('근거','전체보안점검 보고서47쪽 원본을 확대·조합한 기존 시연 장면'),('범위','별도 worktree·모의 공급자 과거 기록. 현재 실행본 전체 재검증 완료와 구분')])
imported('09',P/'DYNAMIC_GUARD_DARK_TABLET.pdf')
imported('10',P/'COST_CONTROL_DARK_TABLET.pdf')
imported('11',P/'ERROR_STOP_FLOW_DARK_TABLET.pdf')
imagepage('12','마케팅 Board · 실제 집계와 시연 분석 분리','DB 문의·검색·현재 준비와 프런트 합성 전환 분석을 별도 탭으로 구분합니다.',ROOT/'output/marketing-demo-2026-10-25/real-desktop.png',[('실제 집계','C13이 조회한 개발 더미 DB 화면. 아래 시드 증가 검증과 촬영 시점이 다릅니다.'),('미측정','단계별 분모가 다르므로 실제 전환율·이탈률로 나누지 않습니다.')])
for key,title in [('M1_journey','가상 고객 여정 → 실제 집계'),('M2_measure','요청·문의·현재 상태 구분'),('M3_integrity','권한·조작·중복 이벤트')]:imagepage('12','마케팅 검증 · '+title,'화면10 마케팅집계 검증 보고서 · 기존20/20 통과 기록',ROOT/f'output/presentation-2026-10-25/nine-minute-marketing-security/assets/{key}.png',[('검증','방문40·작성28·접수20·안내16, 검색13회·고유 문의10건, 현재 준비5건'),('범위','가상 여정의 실제 API·DB 증가 검증. 실제 고객 매출·계약 전환 성과 아님')])
table('12','마케팅 결과 · 운영 해석과 측정 한계','숫자의 기준과 수집 여부를 먼저 확인하고 운영 판단으로 연결합니다.',['대상','결과·의미','구분'],[
['방문·작성·안내','P06 보고 VISIT·INTAKE_STARTED·SUMMARY_VIEWED','React 자동 수집 미연결. 시드로40/28/16 입력'],['문의·검색','신규 접수20 / 요청13회·고유 문의10건','복수 검색을 신규 고객으로 합산하지 않음'],['현재 상담 준비','6건 READY 후1건 CLOSED → 현재5건','현재 상태. 누적 상담·계약 건수 아님'],['분포','유형·다음 단계·지역의 신규20건 분포 일치','실제 집계 로직과 시드 대조. 시장 표본 아님'],['무결성','10건 조작·권한 위반 거절,20/20 통과','이벤트 재전송 중복 제외·ID/내용 충돌409'],['시연 분석','가상40→24→12→4·수임/이탈 사유','별도 합성 자료. API 시드40→28→20→16과 혼합 금지']],[210,540,434],14,'마케팅 시연 검수 / 화면10 마케팅집계 검증 보고서')
timeline=json.loads(src(ROOT/'output/presentation-2026-10-25/nine-minute-marketing-security/timeline.json').read_text(encoding='utf-8'))
def stamp(s):return f'{s//60:02d}:{s%60:02d}'
table('13','시연 결과 · 9분 영상30장면 매칭','제품 → 비용 → 실패·보안 → 운영 집계 → 모바일. PDF 설명은 영상9분에 포함하지 않습니다.',['구간','실험·키워드','증거 유형'],[[stamp(s['start'])+'-'+stamp(s['end']),s['title'],s['mode']] for s in timeline['shots']],[180,550,454],13,'09M_DEMO_FLOW_TIMESTAMP_KEYWORD.md / 최신 timeline.json')
table('13','수행결과 · 구현·검증·후속 범위','구현 기능과 실제 실행 근거를 구분합니다. 이번 문서 생성은 새 회귀 실행이 아닙니다.',['영역','현재 수행 결과','후속 범위'],[
['제품·역할','선택형 접수·규칙 안내·검색·변호사·관리자13화면','운영 사용자·접근성 심화 검증'],['데이터·동시성','소유/배정·메모 비공개·키/해시·버전·전이','MySQL 영속 환경 이행·백업/복구'],['P03 검색','검색문→네이버 두 출처→최대3건→설명·부분 복구','실제 네이버·변협 관련성/설정·품질 별도'],['보안·비용','Fixed/Dynamic·입출력 Guard·Privacy·단계별 비용','모의 Guard≠실제 NeMo 과금. SSE 실공급자 검수 별도'],['OpenAI','과거3회 입력132·출력81; 당시 계산 $0.0000684','Spend0 반올림과 구분. 현재 모델 요금 견적 아님'],['운영·마케팅','C13 DB 집계·실제 시계열 경로·가상 여정 무결성','자동 여정·상담 결과 수집·실측 전환/이탈 후속'],['시연','540초·30장면·1920×1080·무음·디코딩 기록','음성 방식 결정·390px 캡처와 실제 기기 조작 구분'],['확장','RAG·Vector DB·Python actions·요약·후속 알림','계획이며 현재 구현 MVP 기능으로 설명하지 않음']],[175,510,499],13,'Ground Rule / 최신 API·DDD / 기존 검수·실험')
imported('13',P/'PRODUCT_INTRODUCTION_DARK_TABLET.pdf',13,14)
refs=[('제품·PM','00_PRODUCT_INTRODUCTION.md / 04_PM_SECURITY_CORE.md / README.md'),('방법·일정','GROUND_RULE.md / PROJECT_14_DAY_SCHEDULE_2026-10-26.md'),('DB','BACKEND_ERD.md / BACKEND_ERD_SCHEMA_EVIDENCE.sql / DB_DEPLOYMENT_REVIEW_2026-10-23.md'),('API·코드','SCREEN_API_CONTROLLER_MAPPING.md / API_STATUS_SPEC.md / BACKEND_CONCRETE_FLOWS.md'),('화면·권한','SCREEN01_04_INTEGRATION_REVIEW.md / SCREEN05_08_INTEGRATION_REVIEW.md / SCREEN09_12_ADMIN_INTEGRATION_REVIEW.md / SCREEN13_OBSERVABILITY_REVIEW.md'),('DDD','BACKEND_DDD_EVENT_STORMING.md · 2026-10-25 최신판'),('보안·비용·오류','전체보안점검 보고서 / EXPERIMENT_RESULT_MAPPING.md / Cost·Dynamic·Error PDF'),('마케팅','화면10 마케팅집계 검증 보고서 / MARKETING_DEMO_REVIEW_2026-10-25.md'),('시연·자료','09M_DEMO_FLOW_TIMESTAMP_KEYWORD.md / timeline.json / verification.json / 기존 캡처')]
table('13','근거 자료 색인 · 재현·편집 출처','기존 문서·PDF·이미지를 재사용했습니다. 오래된 P03 미구현 설명은 현행 근거로 채택하지 않았습니다.',['영역','근거'],refs,[210,974],14,'SOURCE_MANIFEST.json에 사용 파일별 SHA-256 기록')
end();c.save()
readers={};writer=PdfWriter();ranges={}
for idx,part in enumerate(parts):
 path=part['file']
 if path not in readers:readers[path]=PdfReader(path)
 page=readers[path].pages[part['page']];ow,oh=float(page.mediabox.width),float(page.mediabox.height)
 dest=writer.add_blank_page(W,H);s=min(W/ow,H/oh)
 dest.merge_transformed_page(page,Transformation().scale(s).translate((W-ow*s)/2,(H-oh*s)/2))
 ranges.setdefault(part['section'],[idx+1,idx+1])[1]=idx+1;part['finalPage']=idx+1
for idx,page in enumerate(writer.pages):
 buf=BytesIO();ov=canvas.Canvas(buf,pagesize=(W,H));ov.setFillColor(HexColor(BG));ov.rect(0,0,W,62,fill=1,stroke=0)
 ov.setStrokeColor(HexColor(LINE));ov.line(48,58,1232,58);ov.setFont('KR',10);ov.setFillColor(HexColor(MUTED))
 ov.drawString(50,31,'2026-10-26 | 팀 프로젝트 결과보고서 | '+names.get(parts[idx]['section'],'LEGAL AI INTAKE'))
 ov.drawRightString(1230,31,f'{idx+1:03d} / {len(parts):03d}');ov.save();page.merge_page(PdfReader(buf).pages[0])
buf=BytesIO();ov=canvas.Canvas(buf,pagesize=(W,H));y=184
for code,title in sections:
 first,last=ranges[code];ov.setFillColor(HexColor(PANEL if int(code)%2 else BG));ov.roundRect(48,H-y-45,1184,45,6,fill=1,stroke=0)
 ov.setFont('KRB',17);ov.setFillColor(HexColor(CYAN));ov.drawString(64,H-y-29,code)
 ov.setFont('KR',17);ov.setFillColor(HexColor(TEXT));ov.drawString(115,H-y-29,title);ov.drawRightString(1210,H-y-29,f'{first:03d}-{last:03d}')
 writer.add_annotation(page_number=1,annotation=Link(rect=(48,H-y-45,1232,H-y),target_page_index=first-1));writer.add_outline_item(code+' '+title,first-1);y+=49
ov.setFont('KR',12);ov.setFillColor(HexColor(MUTED));ov.drawString(52,103,'근거 유형: 실제 앱·더미 사례 / 실제 LLM 기록 / 모의 실험 / 계획·미검증을 구분합니다.')
ov.save();writer.pages[1].merge_page(PdfReader(buf).pages[0])
writer.add_metadata({'/Title':'팀 프로젝트 결과보고서 - Legal AI Intake System','/Subject':'제품·PM·Ground Rule·ERD·API·화면13개·DDD·보안·비용·오류·마케팅 통합','/Author':'Legal AI Intake System'})
FINAL=OUT/'LEGAL_AI_INTAKE_TEAM_PROJECT_REPORT_REVISED_V3_2026-10-26.pdf'
with FINAL.open('wb') as f:writer.write(f)
for path in [ROOT/'docs/presentation-2026-10-25/BACKEND_CONCRETE_FLOWS.md',ROOT/'docs/BACKEND_ERD.md',ROOT/'docs/BACKEND_DDD_EVENT_STORMING.md',ROOT/'docs/SCREEN_API_CONTROLLER_MAPPING.md',ROOT/'GROUND_RULE.md',ROOT/'docs/PROJECT_14_DAY_SCHEDULE_2026-10-26.md']:src(path)
md.insert(0,'# 팀 프로젝트 결과보고서 · 편집·재현 원본\n\n2026-10-26 / 전역 페이지는 아래 목차를 따릅니다.\n\n'+'\n'.join(f'- {code} {title}: {ranges[code][0]}~{ranges[code][1]}쪽' for code,title in sections)+'\n\n기존 PDF는 원본 페이지를 재사용했습니다. 신규 표·화면 정의는 본문과 생성 스크립트에서 편집합니다.\n')
md.append('\n# 부록 · 수정 가능한 Mermaid·정책 원문\n')
for path in [ROOT/'docs/presentation-2026-10-25/BACKEND_CONCRETE_FLOWS.md',ROOT/'docs/BACKEND_ERD.md',ROOT/'docs/BACKEND_DDD_EVENT_STORMING.md']:md.append('\n## 원본 '+path.name+'\n\n'+path.read_text(encoding='utf-8'))
(OUT/'TEAM_PROJECT_REPORT_EDITABLE.md').write_text('\n'.join(md).replace('既存PDF','기존 PDF'),encoding='utf-8')
manifest=dict(date='2026-10-26',totalPages=len(parts),sections=[dict(code=code,title=title,start=ranges[code][0],end=ranges[code][1]) for code,title in sections],screens=[dict(number=s[0],name=s[1],image=s[5],provenance=s[6]) for s in specs],numberedApiCount=38,auxiliaryActuatorCount=1,newProviderCalls=0,newServerTests=0,pages=parts,sources=list(sources.values()))
(OUT/'SOURCE_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'LAYOUT_BOUNDS.json').write_text(json.dumps(bounds,ensure_ascii=False,indent=2),encoding='utf-8')
print('CREATED',str(FINAL),'pages',len(parts),'screens13/APIs38',flush=True)
poppler=__import__('shutil').which('pdftoppm') or 'pdftoppm'
r=subprocess.run([poppler,'-scale-to','700','-png',str(FINAL),str(QA/'page')],capture_output=True)
if r.returncode:raise RuntimeError(r.stderr.decode('utf-8',errors='replace'))
frames=sorted(QA.glob('page-*.png'));face=ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',15)
for start in range(0,len(frames),12):
 contact=Image.new('RGB',(1680,1370),BG);draw=ImageDraw.Draw(contact)
 for j,path in enumerate(frames[start:start+12]):
  im=Image.open(path);im.thumbnail((550,305));x=(j%3)*560;y=(j//3)*340
  contact.paste(im,(x+(550-im.width)//2,y+22));draw.text((x+12,y+2),f'PAGE {start+j+1:03d}',font=face,fill=TEXT)
 contact.save(QA/f'contact-{start//12+1:02d}.png')
assert len(frames)==len(parts)
r=PdfReader(FINAL);assert len(r.pages[1].get('/Annots',[]))==13;assert len(r.outline)==13
assert all(p.extract_text().strip() for p in r.pages)
proof=dict(pages=len(r.pages),sections=13,screens=13,apiCount=38,auxiliaryActuator=1,allPagesRendered=len(frames),authoredBoundsChecked=len(bounds),clickableContentsLinks=13,bookmarks=13,textPresentAllPages=True,newProviderCalls=0,newServerTests=0)
(OUT/'REPORT_QA.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(proof,ensure_ascii=False),flush=True)
