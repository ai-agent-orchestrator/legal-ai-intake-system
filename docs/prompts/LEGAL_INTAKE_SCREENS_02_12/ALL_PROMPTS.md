# Legal Intake · Screens 02-12 Design Prompts

2026-10-20 · V4 운영자 엄지척 공통 헤더 · 로그인 화면은 재생성하지 않는다. 화면을 하나씩 요청한다.

같은 ChatGPT 대화의 선택된 로그인 이미지를 참조한다. 새 대화이면 references 폴더의 데스크톱·모바일 이미지를 함께 제공한다. 각 화면의 API 연결은 이전 버전과 동일하다.

운영자 09~12는 references/09_12_OPERATOR_THUMBS_UP_SOURCE.png를 공통 가로 헤더로만 사용한다. references/09_12_OPERATOR_HEADER_CROP_REFERENCE.png는 크롭 구도 참조용이다. 두 번째 원본의 표정·모니터·엄지척을 살리고 상단 큰 제목·아이콘 줄은 크롭 밖으로 완전히 뺀다.

## 공통 기준 · 처음 한 번 적용

```text
You are designing the next screen of an existing React product:
Security-Aware Legal AI Intake System / Legal Intake.

Generate ONE high-fidelity desktop UI mockup image for the screen requested below.
Use Korean product UI text. The deliverable is a usable application screen.

APPROVED VISUAL REFERENCES - THIS SUPERSEDES THE EARLIER GENERIC LOGIN CANDIDATE
Use the user-approved desktop login and mobile login reference images attached
to this conversation as STYLE REFERENCES, not as edit targets.
Reference filenames: 01_LOGIN_DESKTOP_PREFERRED.png and 01_LOGIN_MOBILE_PREFERRED.png.
The user prefers this warm, premium law-office atmosphere over the earlier
flat neutral login mockup. Preserve that specific brand feeling across Screens 02-12.

ATMOSPHERE AND BACKGROUND
A bright contemporary legal consultation office: large daylight windows,
a softly defocused city skyline, warm wood shelves, books, a desk, and a few plants.
Combine cool natural window light with warm interior materials. The result should
feel established, reassuring, polished, and human rather than sterile or flashy.
Reuse one coherent office visual across the product; do not invent a different
luxury interior or a new stock photo for every page.
Place the office photo behind the application shell, at the page edges, or in
a restrained header area. Use bright white overlays and soft background blur.
Keep form surfaces, tables, charts, and detail drawers opaque or nearly opaque
white; never place small text directly over shelves, books, windows, or plants.

BRAND, COLOR AND TYPOGRAPHY
Keep the reference's royal-blue shield/document mark and the product name
Security-Aware Legal AI Intake System. A compact two-line brand lockup is suitable
inside working screens; do not repeat the giant login title above every data table.
Use deep navy #0B2148, royal blue #0B4AB8 / #1456C8, white #FFFFFF,
pale blue #EDF4FF, cool borders #D6DFEC, and muted slate #63738B.
Warm beige/wood tones belong mainly to the atmospheric background.
Use an elegant serif for the English brand and occasional short display headings.
Use highly readable Korean sans-serif for body text, form labels, numbers,
table rows, controls, and longer Korean headings. Do not set every label in a serif.
Primary buttons may use the reference's subtle navy-to-royal-blue gradient.
Use blue line icons, soft circular icon backgrounds, thin borders, soft shadows,
and 12-18px corner radii. Selection states use a pale-blue fill and clear blue border.
Green indicates completion, amber review, and red errors, always with text labels.
The dark API diagrams describe architecture; do not copy their dark theme into this UI.

LAYOUT BY ROLE
Desktop canvas: 1440px wide. Preserve generous spacing and a clear next action.
Client screens 02-05: a welcoming law-office intake experience, four-step
progress, comfortable white content panels, a quieter reference column.
Attorney screens 06-08: keep the same brand and office atmosphere while using
a compact header/sidebar and clean white work surfaces for real case review.
Operator screens 09-12: a standalone professional workspace, a restrained navy
sidebar, and legible white metric/table panels. Keep this shared sidebar:
운영 요약 / 마케팅 집계 / 보안·비용 / 위험 이력·Incident.
Use background photography more subtly on dense attorney/operator screens.
Adapt the login's atmosphere, not its three-column promotional composition:
working screens need room for forms and records, not a feature-list marketing rail.
Keep the UI feasible with reusable React components, one background asset,
CSS overlays, and a responsive grid.

RESPONSIVE CONTINUITY
The mobile reference establishes the same bright office, navy/blue brand,
shield mark, white cards, and touch-friendly controls at a narrower width.
Design the default deliverable as one flat desktop viewport. If a mobile version
is requested later, make a separate approximately 390px viewport: stack cards,
collapse navigation, keep comfortable touch targets and a clear primary action,
and place wide tables in a readable responsive arrangement.
Do not include the reference's physical phone frame, hand, camera island,
status bar, or surrounding photographed device scene in the application UI image.

DATA AND INTERACTION RULES
Use only the fields and actions specified in the screen's API notes.
API numbers, HTTP paths, DTO names, JWTs, and credentials belong in implementation
handoff notes, not in the main customer interface. Operator technical details may
appear where they help investigation. UI role separation does not prove server authorization.
Distinguish unsaved drafts, stored records, loading, empty, unavailable, and error states.
Missing metrics are unavailable, not zero. Design samples are fictional and should
have one unobtrusive label, 디자인 예시 데이터. Production UI must use real responses.
Keep generated IDs as read-only identifiers. Users select records; they do not invent IDs.
Do not invent payments, appointments, messaging, editing, deleting, exports, automatic
LLM execution, or extra backend fields. Keep planned actions visibly disabled.
Treat privacy masking as field-specific; do not claim all content or attachments are anonymized.
Preserve form input on failure and prevent duplicate submissions while a request is pending.
The references define appearance, not new functionality: do not copy a password-reset
link, language selector, JWT badge, or universal security guarantee into these screens
unless the screen's supported workflow actually requires it.

OUTPUT
Render only the requested screen, showing a coherent main state with legible Korean labels.
Do not regenerate Login or output all screens as a small collage. Preserve this visual system
when the next numbered prompt arrives. Do not replace the requested image with a code listing.
Use the API notes to make the design implementable; do not print those notes on the mockup.
```

## 02 · 사건 분류·절차 안내

```text
SCREEN 02 - 사건 분류·절차 안내 / Case Type and Procedure Guide

SCREEN-SPECIFIC VISUAL DIRECTION
Use the bright office scene around a spacious white classification panel. Keep the shield/document brand in the compact header. Use pale-blue category cards with restrained blue line icons and unmistakable selection borders. Place the short description and preview on a calm, high-contrast surface; the procedure reference panel is secondary. Let the office atmosphere welcome the client without becoming a marketing feature list.

PURPOSE AND LAYOUT
Help a client describe a situation without knowing legal categories.
Use the client header and step 1 of 4:
사건 유형 → 문의 작성 → 안내 확인 → 검색 요청.
Heading: 사건 유형을 잘 모르셔도 괜찮습니다.
Subtitle: 가까운 항목을 선택하거나 상황을 짧게 적어 주세요.

MAIN CONTENT
- A compact purpose selector: 절차 안내 / 필요한 자료 확인 / 문서 검토 /
  변호사 선임·계약 검토 / 전자소송 제출 안내. Keep this in the local draft for Screen 03.
- Five category options: 민사 / 형사 / 가사 / 전자소송 / 잘 모르겠어요.
  Show a few example tags, such as 대여금·계약, 사기·경찰 조사, 이혼·양육비.
- A short required description input with a visible character counter.
  Example: 돈을 빌려줬는데 돌려받지 못하고 있어요.
- Button: 사건 유형 확인하기.
- A preview showing returned case type, dispute area, recommended documents,
  and the guidance message. Let the client review the suggestion.
- A secondary panel, 관련 절차 살펴보기: a procedure list and an expandable
  selected procedure detail. Keep this reference material separate from classification.
- Primary footer action: 다음: 문의 작성. Carry the draft and chosen values to Screen 03.

STATES
Show a selected CIVIL / 민사 example. Keep the preview distinct from a saved intake.
Allow UNKNOWN / 미분류 with a helpful prompt to add context. Include loading,
description validation, and an empty/error state for reference data.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C02 POST /api/intakes/classify-preview
  Request: required coreStatement, current max 300 characters; optional caseType.
  Category values: CIVIL, CRIMINAL, FAMILY, ELECTRONIC_LITIGATION, UNKNOWN.
  Response: caseType, caseTypeLabel, disputeArea, recommendedDocuments,
  clientGuideMessage, classificationMethod, persisted.
  Current behavior is RULE_BASED with persisted=false. It creates no intake ID
  and does not make an external LLM call. Do not market it as a live LLM response.
R01 GET /api/legal/procedures
  Read supported procedure code/domain/title/summary/userRole values.
R02 GET /api/legal/procedures/{code}
  Use an actual code returned by R01. Show title, summary, steps, forms as reference.
Purpose selection is not an extra C02 request property; retain it in client state.
Shared P06 POST /api/intake-events can explicitly record VISIT and INTAKE_STARTED
at appropriate client milestones. Use stable eventId values on retries; never
infer that rendering a mockup or a React rerender automatically records an event.
```

## 03 · 문의 등록·자료 준비

```text
SCREEN 03 - 문의 등록·자료 준비 / Structured Intake and Preparation

SCREEN-SPECIFIC VISUAL DIRECTION
Use the same daylight office background softly visible at the margins. Give the structured intake form a generous white work surface with navy section headings, quiet borders, and grouped short fields. Put preparation references in a lighter secondary white panel. Keep the main submit action a restrained navy-to-blue button. The background must never interfere with inputs, checkboxes, field validation, or file progress.

PURPOSE AND LAYOUT
Create a structured intake with short, guided fields. Use client step 2 of 4.
Heading: 상담에 필요한 정보를 정리해 주세요.
Use a spacious main form with a narrower preparation-reference column.

MAIN CONTENT
- A read-only draft summary of the selected case type with a back/edit-selection action.
- Required fields: 문의 목적, 사건 유형, 당사자 위치, 핵심 내용, 원하는 다음 단계.
- Party options: 피해자 / 피고소인 / 원고 / 피고 / 신청인 / 상대방 / 잘 모르겠음.
- Optional fields: 사건 발생일, 상대방, 금액·피해 규모, 현재 진행 단계,
  이름, 연락처, 선호 지역. Put detailed lawyer-search preferences in a collapsed
  optional section rather than overwhelming the main form.
- 보유 자료 checkboxes: 계약서 / 이체 내역 / 문자·대화 / 사진·영상 / 관련 서류 / 없음.
  These declare what the client has; selecting them is not a file upload.
- A preparation panel: 관련 절차, 단계별 체크리스트, 관련 양식 안내.
  Open a form's title and usage note in a drawer. Do not invent a form download URL.
- A concise data-minimization reminder. Use server-provided notices where available.
- Main button: 문의 접수하기. Secondary: 이전.

STATES AND NEXT STEP
Distinguish 작성 중 from 접수 완료. Use returned record ID only after success.
On success, proceed to Screen 04 with that ID. Preserve the draft on validation
or network failure. Do not claim an existing intake was updated by this POST.
If attachments are shown, make their workflow clearly post-registration:
접수 후 파일 첨부, with login/authorization and per-file progress/error states.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C03 POST /api/intakes creates a record and returns 201 + LegalIntakeResponse.id.
  Required: inquiryPurpose, caseType, partyPosition, coreStatement, desiredNextStep.
  Current coreStatement max: 2000 characters. Other displayed field limits must
  follow the request DTO; do not reuse C02's 300 limit for every field.
  Optional: sessionId, clientName, clientPhone, incidentDate, counterpart,
  amountOrDamage, currentStage, preferredRegion, documents, officeType,
  experienceLevel, publicCareerPreference, consultationStyle, lawyerSearchPurpose.
R02 GET /api/legal/procedures/{code}: selected procedure detail.
R03 GET /api/legal/procedures/{code}/steps: step titles/descriptions/checklists.
R04 GET /api/legal/forms: available form metadata.
R05 GET /api/legal/forms/{formCode}: selected form metadata and usage note.
Use actual procedure/form codes returned by reference APIs.
Optional shared P04 POST /api/intakes/{intakeId}/documents uses multipart file
and optional documentType, only after an intake exists. JWT and ownership/admin
checks apply. Default 5 MiB; PDF, PNG, JPEG, UTF-8 TXT with matching content.
P04 failure does not undo a successful C03 registration. Retrying file upload
must not create a second intake. Use its returned metadata/downloadUrl for files.
```

## 04 · 간단 안내·문의 상세

```text
SCREEN 04 - 간단 안내·문의 상세 / Intake Detail and Preparation Guide

SCREEN-SPECIFIC VISUAL DIRECTION
Present the saved inquiry as an elegant, readable consultation-preparation document inside a white panel. Use a small blue document/check icon, navy headings, and pale-blue guide accents. Keep the office image softly visible behind the shell. Let case facts, recommended materials, and next steps carry the visual hierarchy; avoid a giant chatbot illustration or a fresh login-style hero.

PURPOSE AND LAYOUT
Show the stored inquiry and a concise preparation guide after registration.
Use client step 3 of 4. Heading: 문의가 접수되었습니다.
Show a small read-only inquiry number and the returned current status.
Use a clear main reading column and a supporting reference column.

MAIN CONTENT
- Stored inquiry: case type, dispute area, purpose, party position, dates,
  amount/damage, current stage, desired next step, selected documents.
- Render the returned masked fields for names, phone, counterpart, core statement.
- A friendly guide panel: 확인된 내용 / 준비하면 좋은 자료 / 다음 정리 방향.
  Use the returned guidance and checklist text rather than generating legal conclusions.
- Related procedure accordion from R02.
- A criminal supplementary-investigation checklist only when relevant, labeled
  as reference preparation information. Keep R06 separate from a general civil example.
- Server-provided privacy/security notices, with no claim of an LLM safety decision.
- Main action: 검색 요청 준비하기 → Screen 05 with the same intake ID.
  Secondary actions may reveal the stored input or start a clearly labeled 새 문의 작성.

STATES
If guide generation fails, keep the successfully loaded saved inquiry visible
and allow retry of the guide only. If no intake ID exists, direct the user back
to registration. Never show a successful registration before C03 succeeds.
Use a single guide-style result, not an unassigned free-form AI chat window.
Do not add 요약 저장 or 기존 문의 수정 buttons without supporting write APIs.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C05 GET /api/intakes/{id}: LegalIntakeResponse, including clientGuideMessage,
recommendedDocuments, lawyerSearchCondition, consultationMemos, uploadedDocuments.
C04 POST /api/legal-intake/minimal: request fields sessionId, inquiryPurpose,
caseType, partyRole, incidentDate, counterpart, amountOrDamage, documents,
desiredNextStep, coreFact. Map draft partyPosition → partyRole and
coreStatement → coreFact; do not send intakeId as though it is this DTO.
  Response: checkedFacts, caseTypeLabel, disputeArea, recommendedDocuments,
  clientGuideMessage, privacyNotice, securityNotice, plus echoed structured fields.
  This is rule-based guidance, with no external LLM call and no intake DB write.
R02 GET /api/legal/procedures/{code}: related procedure reference.
R06 GET /api/legal/criminal/checklist/supplementary-investigation:
  domain, role, stageLabel, checklist, recommendedAction.
Optional shared P06 POST /api/intake-events explicitly reports SUMMARY_VIEWED
after the result is actually shown; retry with the same eventId.
S01/S02 chat APIs are not assigned to this screen by the supplied mapping.
```

## 05 · 검색 요청·결과·이력

```text
SCREEN 05 - 검색 요청·결과·이력 / Search Requests, Results and History

SCREEN-SPECIFIC VISUAL DIRECTION
Use coordinated white condition, request-history, and saved-result panels over a quiet office backdrop. Use navy headings, readable source links, subtle blue document/search icons, and clear selected-request borders. Keep the active record-creation action prominent and the planned search-execution action visibly muted. Saved results should feel like organized reference records rather than advertisements or lawyer ranking cards.

PURPOSE AND LAYOUT
Use client step 4 of 4. Heading: 변호사 탐색을 위한 조건을 확인하세요.
Arrange a condition summary, request creation area, prior-request list,
and selected-request results in one coherent workspace.

MAIN CONTENT
- Read-only parent inquiry number and saved search conditions: legal field,
  dispute type, preferred region, optional office/experience preferences,
  and the stored search query. Obtain these from the intake response passed forward.
- Optional short input: 검색 목적. Source selection: 공개 정보 출처 정책.
- Main active button: 검색 요청 기록하기.
- A clearly disabled action: 외부 검색 실행 · 준비 중.
- Request-history list: request number, creation time, status, search purpose.
  Selecting a request displays its stored query and result snapshots.
- Result cards: title, source type, snippet, source URL. Use saved data, not
  fabricated endorsements, recommendation scores, or live-search animations.
- Secondary expandable form: 결과 링크 기록. Fields: 출처 유형, 제목, URL,
  짧은 설명, 표시 순서. Label this manual recording of an already obtained result.
- Back action to Screen 04. Note: 공개 정보 탐색을 위한 참고 자료입니다.

STATES
Show a useful empty state, 검색 요청은 기록되었으며 저장된 결과가 없습니다.
Distinguish request creation, manual result recording, and future search execution.
Handle protected history access/login separately from the public request actions;
do not invent an empty successful history when P02 returns an authentication error.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C06 POST /api/intakes/{intakeId}/search-requests
  Body: optional searchPurpose (max 300), sourcePolicy (max 100).
  Creates a search-request record; it does not execute external search or an LLM.
C07 GET /api/intakes/search-requests/{searchRequestId}
  Returns id, intakeId, conditionId, searchPurpose, sourcePolicy, status,
  searchQuery, results, createdAt.
C08 POST /api/intakes/search-requests/{searchRequestId}/results
  Body: required sourceType, title, url; optional snippet, displayOrder.
  Appends a stored snapshot and returns the updated request, not an LLM answer.
P02 GET /api/intakes/{intakeId}/search-requests: protected history list;
  JWT and ownership/admin checks apply.
P03 POST /api/intakes/search-requests/{searchRequestId}/execute is PLANNED.
  Keep its button disabled. Do not call it or imply live search/cost control is complete.
intakeId identifies the parent inquiry; searchRequestId identifies one child request.
Use actual returned IDs. Preserve recorded data if a later operation fails.
```

## 06 · 변호사 문의 목록

```text
SCREEN 06 - 문의 목록 / Attorney Intake Work Queue

SCREEN-SPECIFIC VISUAL DIRECTION
Translate the approved office mood into an attorney workbench: compact shield/brand header, restrained navy navigation, a subtle daylight office strip or margin, and a broad opaque white table. Prioritize readable inquiry snippets, date/time, and status labels. Use compact blue-accent summary cards rather than oversized marketing tiles. The table remains the main visual focus.

PURPOSE AND LAYOUT
Create a practical attorney workspace for reviewing incoming inquiries.
Use the attorney navigation and a wide, readable table. Heading: 접수된 문의.

MAIN CONTENT
- Compact summary cards calculated only from the returned list:
  조회된 문의 / 이 목록의 검토 대기 / 보완 필요 / 상담 준비.
- Clearly label the scope: 최근 최대 50건, 현재 목록 기준.
- Filters for loaded rows: 사건 유형, 현재 상태, 원하는 다음 단계,
  and a local text search. A date filter, if shown, applies only to loaded records.
- Table columns: 문의 번호, 접수 시각, 사건 유형·분쟁 영역,
  핵심 내용 미리보기, 보유 자료 수, 원하는 다음 단계, 상태.
- Use masked text and a restrained detail affordance on each row.
- Primary row action: 상세 보기 → Screen 07 with that row's ID.
- Manual refresh and a clear local 조회 시각.

STATUS LABELS
SUBMITTED → 접수됨, REVIEWED → 검토됨, NEED_MORE_INFO → 보완 필요,
CONSULTATION_READY → 상담 준비, CLOSED → 종료.
Do not use NEW as the stored inquiry status.

STATES
Include loading rows, no inquiries, no matches within the loaded list,
and a connection error with retry. Do not replace a failed load with zero cards.
Do not show appointments, client notifications, bulk status changes, exports,
server pagination, or organization-wide totals that this API does not provide.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C09 GET /api/intakes returns the most recent maximum 50 LegalIntakeResponse objects.
Useful fields: id, caseTypeLabel, disputeArea, coreStatementMasked,
selectedDocuments, desiredNextStep, status, createdAt, and masked client fields.
The response contains inquiry content as well as IDs; it is not an ID-only list.
Filtering/counting the loaded collection is frontend behavior, not a new API.
Selecting a row retains its id and navigates to C05 on Screen 07.
Visual attorney navigation alone must not be presented as proof that all
existing inquiry endpoints enforce attorney-only authorization.
```

## 07 · 변호사 문의 상세

```text
SCREEN 07 - 문의 상세 / Attorney Intake Review

SCREEN-SPECIFIC VISUAL DIRECTION
Create a polished digital case-file workspace: white document-like surfaces, navy section headings, pale-blue context chips, and a restrained blue shield/document identity. Keep the office image at the edges or header. Separate client facts, material lists, saved guidance, and attorney notes with spacing and thin rules. Preserve a strong reading order and generous line height instead of a busy three-column collage.

PURPOSE AND LAYOUT
Help an attorney understand one saved inquiry before consultation.
Use the same attorney workspace as Screen 06. Header: 문의 #1042 상세,
with a fictional read-only ID, returned current status, and creation time.
Use two main columns plus a compact context area, not three cramped tables.

MAIN CONTENT
- 사건 개요: case type, dispute area, inquiry purpose, party position,
  desired next step, current stage.
- 사실관계: incident date, masked counterpart, amount/damage,
  masked core statement. Preserve the client's wording as a saved statement.
- 자료: selected document categories and recommended documents in separate lists.
- 접수 안내: the returned clientGuideMessage. Do not label it a fresh LLM analysis.
- 검색 조건: read-only saved lawyerSearchCondition summary.
- 상담 메모: existing masked memo content, follow-up items, timestamps.
- If returned, 첨부 파일: filename, type, size, uploaded time, download action.
- Primary action: 상태·상담 메모 관리 → Screen 08 with the same inquiry ID.
- Secondary: 목록으로 → Screen 06.

STATES
Show missing optional fields with an unobtrusive 미입력 label.
Do not invent a chronological event log from a single incidentDate and createdAt.
An empty memo section should invite opening Screen 08, not display synthetic notes.
Do not add 보안 통과, 비용 정책 통과, or Privacy 완료 badges based on missing fields.
A file-download error must not remove the inquiry detail already displayed.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C05 GET /api/intakes/{id} is the same read API reused by client Screen 04.
LegalIntakeResponse provides masked identity/core fields, case and preparation
fields, lawyerSearchCondition, consultationMemos, uploadedDocuments, status, createdAt.
This screen reads the record; status and memo writes belong to C10/C11 on Screen 08.
No new intake record or duplicate ID is created by opening the attorney view.
Optional shared P05 GET /api/intakes/{intakeId}/documents/{documentId}/download
  downloads an existing attachment. Use returned IDs/downloadUrl, not a URL made
  from a filename. JWT and ownership/admin checks apply; preserve 401/403 feedback.
Do not promise a download to a role that the server has not authorized.
Do not add search-history calls to the required C05-only screen; link to Screen 05
if a separate authorized history workflow is later exposed.
```

## 08 · 상태 변경·상담 메모

```text
SCREEN 08 - 상태 변경·상담 메모 / Status and Consultation Notes

SCREEN-SPECIFIC VISUAL DIRECTION
Use a focused white writing surface with the office background only faintly visible outside the work area. Clearly separate the status panel from the consultation-note editor. Use the same refined navy/blue controls, a quieter outlined status-save action, and a clear note-save button within its own panel. Make follow-up checkboxes, character count, independent save feedback, and unsaved text easy to see.

PURPOSE AND LAYOUT
Create a focused attorney work screen linked to Screen 07.
Keep a compact inquiry header with the same read-only ID and current status.
Use two distinct save areas so the user understands what has been committed.

STATUS PANEL
- Label: 문의 상태.
- Show current status and a next-status selector with Korean labels.
- Choices reflect SUBMITTED, REVIEWED, NEED_MORE_INFO, CONSULTATION_READY, CLOSED.
- Button: 상태 변경 저장.
- A concise result: 변경 전 → 변경 후, or 변경 없음.
- Preserve conflict feedback without claiming the change succeeded.
- CLOSED inquiries cannot change to another status; returning to SUBMITTED is
  disallowed from other statuses. Keep the current status visible when editing is locked.

CONSULTATION NOTE PANEL
- Heading: 상담 준비 메모.
- Large but bounded text area, max 4000 characters, with a counter.
- A separate follow-up checklist:
  자료 보완 요청 / 상담 일정 확인 필요 / 서면 검토 필요 / 전자소송 안내 필요.
- Button: 메모 추가 저장.
- Show existing saved notes as read-only, masked entries with timestamps.
- Explain through layout that the written note and follow-up flags are separate fields.
  Checking a flag does not send a message, book an appointment, or generate note content.

STATES AND NAVIGATION
Show independent pending/success/error states for the two saves.
If one succeeds and the other fails, preserve the success and the remaining draft.
Do not represent the two HTTP requests as one atomic transaction.
Use a back action to Screen 07 and a local unsaved-changes indication where useful.
Do not invent editing or deleting existing memos. A closed inquiry's status lock
does not by itself mean the current memo-creation API rejects new notes.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C10 PATCH /api/intakes/{id}/status
  Body: {status}. Response: id, previousStatus, status, changed.
  Allowed same-state requests can be unchanged; invalid transitions can return 409.
C11 POST /api/intakes/{id}/memo
  Body: required content (max 4000), followUpItems (max 4).
  Codes: REQUEST_DOCUMENTS, SCHEDULE_CONSULTATION, DOCUMENT_REVIEW, E_LITIGATION_GUIDE.
  Response: id, intakeId, contentMasked, followUpItems, createdAt.
Both operations use the same parent inquiry ID already selected on Screens 06/07.
After a successful write, explicitly reread C05 for Screen 07; the backend does
not automatically invoke another screen's HTTP API. Use C05 for contextual reads.
```

## 09 · 운영 요약

```text
SCREEN 09 - 운영 요약 / Operator Overview

SHARED THUMBS-UP HEADER - REQUIRED FOR SCREENS 09, 10, 11 AND 12
Use the SECOND attached image as the actual photo source:
09_12_OPERATOR_THUMBS_UP_SOURCE.png. It shows a dashboard monitor on the left,
a smiling man pointing at it in the center, and a smiling woman giving a thumbs-up
on the right in a bright law office. This replaces the earlier handshake photo.
Use the FIRST image, 09_12_OPERATOR_HEADER_CROP_REFERENCE.png, ONLY as a reference
for a wide, shallow horizontal header composition, not as the actual photo source.

Crop the second photo into one compact panoramic banner above the dashboard content.
Keep the monitor recognizable, both people's expressions visible, and especially
the woman's raised thumb inside the frame. Choose the focal crop around these subjects.
Aim for the first reference's shallow-header feel, but allow a slightly taller banner
when necessary to retain the faces and thumbs-up together. Do not blindly force its
exact aspect ratio, cut off the gesture, stretch the photo, or substitute new people.
Use a focal crop of the original photo, not a regenerated or rearranged scene.

Crop the source's large upper Legal Intake System title and icon row out COMPLETELY;
do not leave clipped letters or fragments of that row along the header edge.
Small lettering naturally visible inside the photographed monitor can remain.
Exclude the first reference's screenshot editor controls, including 변경하기,
위치 변경 and the download icon. Those are not application UI.

Use this cropped photo as a HEADER ONLY, not as a full-page background.
Reuse the same source image, focal crop, banner height and placement across all four
operator screens, together with the same navy sidebar and navy/royal-blue brand.
Place the page-specific title in a separate clear row below the banner. Put KPI cards,
filters, charts, tables and action buttons on opaque white surfaces below that row;
never overlay them on faces, hands or the monitor. Keep the header compact enough
to leave useful workspace for the data. Only the active menu, title and data change.
If mobile variants are requested later, use a proportionally taller responsive header
crop to retain the faces and thumb; keep the page title and data outside the image.
Plan one reusable OperatorLayout/OperatorHeader for later React implementation.
Preserve all existing screen functions, API mappings and interaction states.

SCREEN-SPECIFIC VISUAL DIRECTION
Establish the shared operator layout with a compact panoramic header cropped from 09_12_OPERATOR_THUMBS_UP_SOURCE.png, using the first attachment only as a crop-layout reference. Retain the monitor, both expressions and the raised thumb. Below a separate page-title row, place four crisp white summary cards with blue circular icons, navy numerals, and restrained risk-status colors. Keep risk bars and three navigation cards legible. Reuse this header and navy sidebar on Screens 10-12; leave generous workspace for a short demonstration.

PURPOSE AND LAYOUT
Create the entry page of a standalone operator workspace suitable for a short demo.
Use the shared operator sidebar, a restrained heading, manual refresh,
and a small 조회 시각. Heading: 운영 현황을 한눈에 확인하세요.

MAIN CONTENT
- Exactly four primary totals from C12:
  Agent 실행 이력, 위험 판단 이력, 보안 이벤트, 미확인 Incident.
- A riskSummary section: HIGH / MEDIUM / LOW counts and total,
  with readable horizontal bars and Korean labels 높음 / 보통 / 낮음.
- Compact decision and recommended-action distributions, if useful,
  using riskSummary.decisionCounts and recommendedActionCounts.
- Three short navigation cards, not extra invented statistics:
  마케팅 흐름 파악 → Screen 10,
  보안·비용 확인 → Screen 11,
  위험 이력·대응 관리 → Screen 12.
- Clicking the open-Incident count may navigate to Screen 12's list.
  It must not imply C12 supplied individual incident details.

DATA MEANING
Counts describe saved records. riskSummary is a read-time aggregation.
미확인 Incident counts only OPEN, not all unresolved states.
An ACKNOWLEDGED incident may still need work even though it leaves the OPEN count.
Reading this dashboard does not run a new risk analysis or LLM call.

STATES
Show coherent zero/empty states, loading placeholders, and an error/retry state.
Do not invent total inquiries, today's inquiries, daily trends, uptime,
recent incident rows, recent alerts, or conversion rates from this response.
Keep this overview concise enough to explain in three demo talking points.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C12 GET /api/admin/dashboard/summary
  Fields: agentRunCount, riskHistoryCount, securityEventCount,
  openIncidentCount, riskSummary.
  riskSummary: total, high, medium, low, decisionCounts, recommendedActionCounts.
AdminDashboardService composes repository counts, RiskIncidentService.countOpen(),
and AgentRiskHistoryQueryService.summarize(). C15 reuses that last aggregation.
C12 itself is the only required data API for this screen.
Navigation to other operator screens triggers their own APIs separately.
Operator styling and menu placement do not establish backend admin-only access.
```

## 10 · 마케팅 집계

```text
SCREEN 10 - 마케팅 집계 / Marketing Activity and Intake Journey

SHARED THUMBS-UP HEADER - REQUIRED FOR SCREENS 09, 10, 11 AND 12
Use the SECOND attached image as the actual photo source:
09_12_OPERATOR_THUMBS_UP_SOURCE.png. It shows a dashboard monitor on the left,
a smiling man pointing at it in the center, and a smiling woman giving a thumbs-up
on the right in a bright law office. This replaces the earlier handshake photo.
Use the FIRST image, 09_12_OPERATOR_HEADER_CROP_REFERENCE.png, ONLY as a reference
for a wide, shallow horizontal header composition, not as the actual photo source.

Crop the second photo into one compact panoramic banner above the dashboard content.
Keep the monitor recognizable, both people's expressions visible, and especially
the woman's raised thumb inside the frame. Choose the focal crop around these subjects.
Aim for the first reference's shallow-header feel, but allow a slightly taller banner
when necessary to retain the faces and thumbs-up together. Do not blindly force its
exact aspect ratio, cut off the gesture, stretch the photo, or substitute new people.
Use a focal crop of the original photo, not a regenerated or rearranged scene.

Crop the source's large upper Legal Intake System title and icon row out COMPLETELY;
do not leave clipped letters or fragments of that row along the header edge.
Small lettering naturally visible inside the photographed monitor can remain.
Exclude the first reference's screenshot editor controls, including 변경하기,
위치 변경 and the download icon. Those are not application UI.

Use this cropped photo as a HEADER ONLY, not as a full-page background.
Reuse the same source image, focal crop, banner height and placement across all four
operator screens, together with the same navy sidebar and navy/royal-blue brand.
Place the page-specific title in a separate clear row below the banner. Put KPI cards,
filters, charts, tables and action buttons on opaque white surfaces below that row;
never overlay them on faces, hands or the monitor. Keep the header compact enough
to leave useful workspace for the data. Only the active menu, title and data change.
If mobile variants are requested later, use a proportionally taller responsive header
crop to retain the faces and thumb; keep the page title and data outside the image.
Plan one reusable OperatorLayout/OperatorHeader for later React implementation.
Preserve all existing screen functions, API mappings and interaction states.

SCREEN-SPECIFIC VISUAL DIRECTION
Reuse the exact shared operator header image and sidebar composition from Screen 09. Place journey stages and distributions below the image on clean white panels with navy headings and restrained blue bars. Use equal-weight stage cards with clear measurement labels, not an exaggerated narrowing funnel. Keep the photo out of plot areas and measurement labels so event counts, saved records, and current status remain distinguishable.

PURPOSE AND LAYOUT
Use the shared operator workspace. Heading: 문의 접수 여정과 관심 분야.
Help an operator understand recorded activity without overstating conversion analytics.

MAIN CONTENT
- Four summary cards with distinct labels:
  접수된 문의, 검색 요청 건수, 검색을 요청한 문의 수, 현재 상담 준비 상태.
- Six equal-width stage rows/cards, in a visually readable journey:
  방문 → 문의 작성 시작 → 문의 접수 → 안내 확인 → 변호사 검색 요청 → 상담 준비.
- Every stage must show its count AND measurement label:
  보고된 이벤트 / 저장된 문의 / 고유 문의 / 현재 상태.
- Use independent bars or cards. Do not force counts into a decreasing funnel shape.
- Three distributions: 사건 유형, 원하는 다음 단계, 선호 지역.
- A small scope label: 현재 DB 전체 기록 기준.
- A clearly visible note: 단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.
- Refresh and navigation back to Screen 09; keep other operator links consistent.

MEASUREMENT RULES
Search requests count rows; searched inquiries count distinct intake IDs.
Consultation-ready count is current status, not cumulative completed consultations.
VISIT, INTAKE_STARTED, SUMMARY_VIEWED count explicitly reported P06 events.
They are not inferred unique users or automatic analytics pageviews.
Preferred-region distribution is submitted preference, not geolocation.

STATES
Include no events yet, no saved inquiries, partial missing values, and load error.
Do not show unsupported date-range analytics, conversion percentages, drop-off
rankings, ROI, advertising attribution, recommendations generated by an LLM,
or numbers from a browser session presented as whole-system statistics.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C13 GET /api/admin/dashboard/marketing-funnel
  submittedIntakeCount, searchRequestCount, searchRequestedIntakeCount,
  consultationReadyIntakeCount, stages, caseTypeDistribution,
  desiredNextStepDistribution, regionDistribution, measurementBasis,
  dropOffMeasured, eventCounts.
stages[]: stage, count, measurement.
VISIT / INTAKE_STARTED / SUMMARY_VIEWED → REPORTED_EVENT.
INTAKE_SUBMITTED → STORED_INTAKE.
LAWYER_SEARCH_REQUESTED → DISTINCT_INTAKE.
CONSULTATION_READY → CURRENT_STATUS.
Current measurementBasis: ALL_RECORDS_IN_CURRENT_DATABASE; dropOffMeasured=false.
Opening this GET page must not create new marketing events or run AI analysis.
```

## 11 · 보안·위험·비용 지표

```text
SCREEN 11 - 보안·위험·비용 지표 / Security, Risk and AI Cost

SHARED THUMBS-UP HEADER - REQUIRED FOR SCREENS 09, 10, 11 AND 12
Use the SECOND attached image as the actual photo source:
09_12_OPERATOR_THUMBS_UP_SOURCE.png. It shows a dashboard monitor on the left,
a smiling man pointing at it in the center, and a smiling woman giving a thumbs-up
on the right in a bright law office. This replaces the earlier handshake photo.
Use the FIRST image, 09_12_OPERATOR_HEADER_CROP_REFERENCE.png, ONLY as a reference
for a wide, shallow horizontal header composition, not as the actual photo source.

Crop the second photo into one compact panoramic banner above the dashboard content.
Keep the monitor recognizable, both people's expressions visible, and especially
the woman's raised thumb inside the frame. Choose the focal crop around these subjects.
Aim for the first reference's shallow-header feel, but allow a slightly taller banner
when necessary to retain the faces and thumbs-up together. Do not blindly force its
exact aspect ratio, cut off the gesture, stretch the photo, or substitute new people.
Use a focal crop of the original photo, not a regenerated or rearranged scene.

Crop the source's large upper Legal Intake System title and icon row out COMPLETELY;
do not leave clipped letters or fragments of that row along the header edge.
Small lettering naturally visible inside the photographed monitor can remain.
Exclude the first reference's screenshot editor controls, including 변경하기,
위치 변경 and the download icon. Those are not application UI.

Use this cropped photo as a HEADER ONLY, not as a full-page background.
Reuse the same source image, focal crop, banner height and placement across all four
operator screens, together with the same navy sidebar and navy/royal-blue brand.
Place the page-specific title in a separate clear row below the banner. Put KPI cards,
filters, charts, tables and action buttons on opaque white surfaces below that row;
never overlay them on faces, hands or the monitor. Keep the header compact enough
to leave useful workspace for the data. Only the active menu, title and data change.
If mobile variants are requested later, use a proportionally taller responsive header
crop to retain the faces and thumb; keep the page title and data outside the image.
Plan one reusable OperatorLayout/OperatorHeader for later React implementation.
Preserve all existing screen functions, API mappings and interaction states.

SCREEN-SPECIFIC VISUAL DIRECTION
Reuse the exact shared thumbs-up header crop and sidebar composition from Screen 09. Use opaque white panels below the separate page-title row for risk, security events, cost, and latency, with blue headings and limited green/amber/red status accents. Keep credential inputs discreet in a settings drawer. Preserve readable metric values, missing-data states, and authentication feedback without overlaying operational details on the photo.

PURPOSE AND LAYOUT
Use the shared operator workspace. Heading: 보안 상태와 AI 사용 비용.
Clearly separate stored DB records from current server metrics.
Use three sections that can load or fail independently.

RISK SUMMARY
Show total saved risk judgments, HIGH / MEDIUM / LOW distribution,
decision counts, and recommended-action counts from C15.
Label these stored risk-analysis records, not a count of every live AI request.

SECURITY EVENTS
A recent-events table: 발생 시각, 이벤트 유형, 요청 메서드·경로, 상태, 사용자.
Use the C14 fields. Add a clear link to Screen 12 for risk/Incident investigation.
Do not fabricate reasonCode, sessionId, or input payload columns in this table.

COST AND LATENCY
- Separate reported actual cost, estimated usage settlement, accounted total,
  and in-flight reservations. Show configured monthly/daily/session budgets when present.
- Optional stage breakdown: input guardrail, LLM, output guardrail, if metrics exist.
- Optional prompt/completion token counters only when the returned metrics support them.
- Label cumulative timer averages accurately. Do not invent P95 or historical charts.
- C16 requires ADMIN Basic authentication. Provide a compact connection drawer
  or settings area; mask password input and keep credentials out of saved UI state.
- Show unqueried or missing values as 미조회 / 미수집 or —, never fake zero.
  Use fully Korean labels on the rendered screen.

STATES
Support C16 not configured, authentication failure, and missing samples while
keeping successfully loaded C14/C15 content visible. Use each source's own refresh time.
No universal 보안 정상 badge based merely on a successful fetch or an empty event list.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C14 GET /api/admin/dashboard/security-events
  Up to 50 SecurityEventHistoryResponse rows: id, eventType, method, uri,
  status, username, createdAt.
C15 GET /api/agent/risk/summary
  total, high, medium, low, decisionCounts, recommendedActionCounts.
C16 GET /actuator/prometheus returns text, not JSON, using ADMIN Basic, not JWT.
  Existing metric families include ai.cost.accounted.usd, ai.cost.actual.usd,
  ai.cost.estimated.usage.usd, ai.cost.reserved.usd, ai.cost.budget.usd,
  ai.cost.daily.budget.usd, ai.cost.session.budget.usd, ai.cost.stage.*,
  ai.chat.duration, ai.chat.stage.duration, ai.llm.tokens and block counters.
  Prometheus uses normalized sample names; show only samples actually returned.
  An average can use timer sum/count where count > 0. These timers do not prove
  SSE end-to-end latency, and process metrics are not durable DB audit records.
The configured session budget is not automatically a per-request budget.
```

## 12 · 위험 이력·Incident 대응

```text
SCREEN 12 - 위험 이력·Incident 대응 / Risk Audit and Incident Response

SHARED THUMBS-UP HEADER - REQUIRED FOR SCREENS 09, 10, 11 AND 12
Use the SECOND attached image as the actual photo source:
09_12_OPERATOR_THUMBS_UP_SOURCE.png. It shows a dashboard monitor on the left,
a smiling man pointing at it in the center, and a smiling woman giving a thumbs-up
on the right in a bright law office. This replaces the earlier handshake photo.
Use the FIRST image, 09_12_OPERATOR_HEADER_CROP_REFERENCE.png, ONLY as a reference
for a wide, shallow horizontal header composition, not as the actual photo source.

Crop the second photo into one compact panoramic banner above the dashboard content.
Keep the monitor recognizable, both people's expressions visible, and especially
the woman's raised thumb inside the frame. Choose the focal crop around these subjects.
Aim for the first reference's shallow-header feel, but allow a slightly taller banner
when necessary to retain the faces and thumbs-up together. Do not blindly force its
exact aspect ratio, cut off the gesture, stretch the photo, or substitute new people.
Use a focal crop of the original photo, not a regenerated or rearranged scene.

Crop the source's large upper Legal Intake System title and icon row out COMPLETELY;
do not leave clipped letters or fragments of that row along the header edge.
Small lettering naturally visible inside the photographed monitor can remain.
Exclude the first reference's screenshot editor controls, including 변경하기,
위치 변경 and the download icon. Those are not application UI.

Use this cropped photo as a HEADER ONLY, not as a full-page background.
Reuse the same source image, focal crop, banner height and placement across all four
operator screens, together with the same navy sidebar and navy/royal-blue brand.
Place the page-specific title in a separate clear row below the banner. Put KPI cards,
filters, charts, tables and action buttons on opaque white surfaces below that row;
never overlay them on faces, hands or the monitor. Keep the header compact enough
to leave useful workspace for the data. Only the active menu, title and data change.
If mobile variants are requested later, use a proportionally taller responsive header
crop to retain the faces and thumb; keep the page title and data outside the image.
Plan one reusable OperatorLayout/OperatorHeader for later React implementation.
Preserve all existing screen functions, API mappings and interaction states.

SCREEN-SPECIFIC VISUAL DIRECTION
Reuse the exact shared operator header image and sidebar composition from Screen 09. Place white risk-history and Incident panels below it with navy table headings, subtle row dividers, and a pale-blue selected row. Open investigation details in a clean white drawer. Keep confirmation/resolution actions distinct from legal inquiry status. The shared photo remains in the header while trace IDs, timestamps, signals, and status feedback remain on readable white surfaces.

PURPOSE AND LAYOUT
Use the shared operator workspace. Heading: 위험 판단을 추적하고 대응하세요.
Incident means a system security/operational response item, not a client's legal case.
Use tabs or two clear sections: 위험 판단 이력 and 보안 Incident.

RISK HISTORY
- Table: 시각, 위험 이력 번호, 도구, 위험 수준, 점수, 판정, 권고 조치, Trace ID.
- A detail drawer: signals, recommended action, guardrailReady and trace reference.
- A risk filter supporting either 위험 수준 or 판정 as the server filter mode.
- Do not expose raw userInput by default. No claim that the API already sanitizes it.
- Do not invent per-row sessionId, userId, tokens, fallback text, or reasonCode
  when the risk-history DTO has no such fields.

INCIDENT LIST AND DETAIL
- Table: Incident 번호, 발생 시각, 유형, 심각도, 상태, 위험 점수.
- Selecting a row opens a same-screen detail drawer using that loaded record.
- Detail: incidentType, severity, status, decision, riskScore, recommendedAction,
  riskHistoryId, traceId, message, createdAt, updatedAt.
- Use status labels OPEN → 미확인, ACKNOWLEDGED → 확인됨, RESOLVED → 해결됨.
- Separate buttons: 확인 처리 and 해결 처리. Show pending/success/error states.
- For safe UI behavior, offer 확인 for OPEN rows and disable repeated 해결
  for RESOLVED rows. Do not claim the server implements a strict transition machine.
- A successful patch refreshes the list; the overview's OPEN count is refreshed
  through C12 separately. Acknowledging is not the same as resolving.

STATES AND NAVIGATION
Include empty results, selected record, no selection, and partial failure states.
On ambiguous write failure, reread status before encouraging a repeat action.
Preserve investigation context and local filters after refresh where possible.
Back link to Screen 11 and normal operator navigation.
Do not add a legal-case closure action or a nonexistent Incident detail GET API.

API HANDOFF - DO NOT DISPLAY ON THE PRODUCT SCREEN
C17 GET /api/agent/risk/history
  Optional riskLevel and decision; current backend gives riskLevel precedence.
  With neither, latest max 50 rows. Filtered queries have their own returned scope.
  Fields: id, userInput, toolName, decision, riskScore, riskLevel, signals,
  recommendedAction, guardrailReady, traceId, createdAt.
C18 GET /api/incidents: recent max 50 RiskIncidentResponse records.
C19 PATCH /api/incidents/{incidentId}/ack: no JSON body required.
C20 PATCH /api/incidents/{incidentId}/resolve: no JSON body required.
Frontend selection retains the C18 row's id as incidentId; selection itself
has no API number and makes no server write. riskHistoryId is a separate link.
Use loaded C17 records for an optional matching risk reference; do not invent
/risk/history/{id}. After a patch explicitly reread C18 and, when updating overview,
C12. A patch does not automatically call those HTTP endpoints for the frontend.
```
