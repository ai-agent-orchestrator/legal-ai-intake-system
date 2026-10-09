import { useEffect, useMemo, useState } from "react";
import {
  DOMAIN_OPTIONS,
  fetchProcedureDetail,
  fetchProcedures,
  fetchSupplementaryInvestigationChecklist
} from "./api/legalProcedureApi.js";
import ChecklistPanel from "./components/ChecklistPanel.jsx";
import ProcedureDetail from "./components/ProcedureDetail.jsx";
import ProcedureList from "./components/ProcedureList.jsx";
import StatusBanner from "./components/StatusBanner.jsx";

const DEFAULT_API_BASE = "http://localhost:8080";

export default function App() {
  /*
     Vite React 버전의 핵심 구조.

     procedures:
     GET /api/legal/procedures 로 받은 목록 원본

     selectedCode:
     사용자가 목록에서 클릭한 절차 code
     나중에 React Router를 붙이면 useParams().code 와 같은 역할을 하게 된다.

     detail:
     GET /api/legal/procedures/{code} 로 받은 상세 JSON

     checklist:
     실용형 API인 형사 보완수사 checklist 응답
   */
  const [apiBase, setApiBase] = useState(DEFAULT_API_BASE);
  const [domain, setDomain] = useState("ALL");
  const [keyword, setKeyword] = useState("");
  const [procedures, setProcedures] = useState([]);
  const [selectedCode, setSelectedCode] = useState("");
  const [detail, setDetail] = useState(null);
  const [checklist, setChecklist] = useState(null);
  const [status, setStatus] = useState("idle");
  const [detailStatus, setDetailStatus] = useState("idle");
  const [checklistStatus, setChecklistStatus] = useState("idle");
  const [error, setError] = useState(null);
  const [detailError, setDetailError] = useState(null);
  const [checklistError, setChecklistError] = useState(null);
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    /*
       목록 API 호출 패턴.

       처음 화면이 열릴 때 실행된다.
       domain 필터나 apiBase나 reloadTick 이 바뀌면 다시 실행된다.

       React:
       useEffect -> fetchProcedures -> setProcedures

       Spring:
       GET /api/legal/procedures
       GET /api/legal/procedures?domain=CIVIL
     */
    let alive = true;

    async function loadProcedures() {
      try {
        setStatus("loading");
        setError(null);

        const list = await fetchProcedures(apiBase, domain);

        if (alive) {
          setProcedures(list);
          setStatus("success");

          if (!list.some((item) => item.code === selectedCode)) {
            setSelectedCode("");
            setDetail(null);
          }
        }
      } catch (e) {
        if (alive) {
          setError(e.message);
          setStatus("error");
        }
      }
    }

    loadProcedures();

    return () => {
      alive = false;
    };
  }, [apiBase, domain, reloadTick]);

  useEffect(() => {
    /*
       상세 API 호출 패턴.

       selectedCode 가 바뀌면 해당 code 기준으로 상세 API를 다시 호출한다.

       list/detail 구조:
       목록 클릭 -> selectedCode 변경 -> detail fetch -> setDetail -> 화면 갱신
     */
    if (!selectedCode) {
      return;
    }

    let alive = true;

    async function loadDetail() {
      try {
        setDetailStatus("loading");
        setDetailError(null);

        const data = await fetchProcedureDetail(apiBase, selectedCode);

        if (alive) {
          setDetail(data);
          setDetailStatus("success");
        }
      } catch (e) {
        if (alive) {
          setDetailError(e.message);
          setDetailStatus("error");
        }
      }
    }

    loadDetail();

    return () => {
      alive = false;
    };
  }, [apiBase, selectedCode]);

  async function loadSupplementaryChecklist() {
    /*
       실용형 API 버튼.

       이건 다음 LLM 상담 챗봇 프로젝트에서 바로 재사용하기 좋다.
       사용자가 "보완수사 단계"라고 말하면 백엔드가 이 checklist를 찾아
       챗봇 응답의 구조화된 근거로 쓸 수 있다.
     */
    try {
      setChecklistStatus("loading");
      setChecklistError(null);

      const data = await fetchSupplementaryInvestigationChecklist(apiBase);

      setChecklist(data);
      setChecklistStatus("success");
    } catch (e) {
      setChecklistError(e.message);
      setChecklistStatus("error");
    }
  }

  const filteredProcedures = useMemo(() => {
    /*
       검색 결과 계산.

       procedures 는 서버에서 받은 원본 state.
       keyword 는 사용자가 입력한 검색어 state.
       filteredProcedures 는 둘을 조합해서 만든 계산 결과다.
     */
    const normalizedKeyword = keyword.trim().toLowerCase();

    if (!normalizedKeyword) {
      return procedures;
    }

    return procedures.filter((procedure) => {
      return [
        procedure.code,
        procedure.domain,
        procedure.title,
        procedure.summary,
        procedure.userRole
      ].some((value) => String(value).toLowerCase().includes(normalizedKeyword));
    });
  }, [procedures, keyword]);

  return (
    <main>
      <header>
        <h1>Legal Procedure Viewer</h1>
        <p>
          Legal procedure DB -&gt; Spring Boot API -&gt; React list/detail viewer.
          This Vite app is the practical frontend bridge for the legal knowledge base.
        </p>
      </header>

      <div className="layout">
        <section>
          <h2>Procedure List</h2>

          <label htmlFor="apiBase">Spring Boot API Base URL</label>
          <input
            id="apiBase"
            type="text"
            value={apiBase}
            onChange={(event) => setApiBase(event.target.value)}
          />

          <label htmlFor="domain">Domain Filter</label>
          <select
            id="domain"
            value={domain}
            onChange={(event) => setDomain(event.target.value)}
          >
            {DOMAIN_OPTIONS.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>

          <label htmlFor="keyword">Search</label>
          <input
            id="keyword"
            type="text"
            value={keyword}
            placeholder="civil, criminal, 소송, 보완수사..."
            onChange={(event) => setKeyword(event.target.value)}
          />

          <div className="actions">
            <button
              type="button"
              onClick={() => setReloadTick((value) => value + 1)}
              disabled={status === "loading"}
            >
              Reload
            </button>
            <button
              type="button"
              className="secondary"
              onClick={() => {
                setKeyword("");
                setDomain("ALL");
              }}
            >
              Reset filters
            </button>
          </div>

          <StatusBanner
            status={status}
            idleTitle="Waiting for API"
            loadingTitle="Loading procedures..."
            successTitle="Procedure list loaded"
            errorTitle="Procedure API failed"
            message={`${filteredProcedures.length} procedures visible. Selected: ${selectedCode || "none"}`}
            error={error}
          />

          <ProcedureList
            status={status}
            procedures={filteredProcedures}
            selectedCode={selectedCode}
            onSelect={setSelectedCode}
          />
        </section>

        <section>
          <h2>Procedure Detail</h2>

          {!selectedCode && (
            <div className="empty">Select a legal procedure from the list.</div>
          )}

          {selectedCode && (
            <>
              <StatusBanner
                status={detailStatus}
                idleTitle="Waiting for selection"
                loadingTitle="Loading detail..."
                successTitle="Procedure detail loaded"
                errorTitle="Detail API failed"
                message={`Detail loaded for ${selectedCode}.`}
                error={detailError}
              />

              {detailStatus === "success" && detail && (
                <ProcedureDetail detail={detail} />
              )}
            </>
          )}

          <div className="actions">
            <button
              type="button"
              className="secondary"
              onClick={loadSupplementaryChecklist}
              disabled={checklistStatus === "loading"}
            >
              Load Criminal Checklist
            </button>
          </div>

          {checklistStatus === "error" && (
            <StatusBanner
              status="error"
              idleTitle=""
              loadingTitle=""
              successTitle=""
              errorTitle="Checklist API failed"
              message=""
              error={checklistError}
            />
          )}

          <ChecklistPanel checklist={checklist} />

          <h3>Raw Detail JSON</h3>
          <pre>{detail ? JSON.stringify(detail, null, 2) : "No detail selected yet."}</pre>
        </section>
      </div>
    </main>
  );
}
