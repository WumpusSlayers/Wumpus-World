# 👾 Wumpus World AI - 백엔드(Core) 주요 변경 사항 및 팀 공유 문서

팀원들과 공유하여 원활한 연동 및 협업이 가능하도록 백엔드(`Wumpus-World-Core`) 프로젝트에서 구현된 주요 변경 사항, DTO 추가, API 응답 포맷 개선 사항 및 테스트 보강 세부 내역을 정리했습니다.

---

## 1. 백엔드 개편 개요 (Overview)

프론트엔드(`Wumpus-World-Client`)의 임시 목업 데이터베이스를 완전히 걷어내고, **실제 Spring Boot 백엔드 서버와 실시간 REST API 연동**을 처리하면서 다음의 기능들을 완벽히 지원하기 위해 백엔드를 보강했습니다.
1. **실시간 KB 스냅샷 지도 고도화:** 에이전트 머릿속(KB)의 16개 격자별 세부 상태(방문 여부, 안전 여부, Pit/Wumpus 후보 및 확정 판정)를 한 번에 전달하도록 DTO 구조 개편.
2. **AI 탐색 의사결정 시각화:** PathFinder가 현재 상황에서 다음 셀을 추천할 때의 판단 기준(1~4순위)을 프론트엔드가 실시간 수신하여 시각화할 수 있도록 로그 포맷 정교화.
3. **액션 피드백 포맷 정렬:** 이벤트 알림(사망 등), 사격 결과(비명 등), 자동 실행 프리픽스 규칙을 마련하여 프론트엔드 터미널 로거와 동기화.

---

## 2. 모듈별 상세 변경 내역 (Detailed Changes)

### 📂 1) Reasoning & KB 추론 관련 추가 및 변경
> **목적:** 4x4 격자 전 영역에 대한 에이전트의 지식 베이스(KB) 추론 현황을 일목요연하게 전달하여 프론트엔드가 "에이전트 머릿속 격자 맵"을 실시간 렌더링하도록 돕습니다.

* **`KbCellDetail.java` [NEW]**
  - **경로:** `src/main/java/com/wumpusslayers/wumpusworld/reasoning/dto/response/KbCellDetail.java`
  - **설명:** 개별 좌표(x, y)에 대응하는 에이전트의 추론 상태를 담는 신규 DTO입니다.
  - **필드:**
    - `int x`, `int y`: 셀 좌표
    - `boolean visited`: 방문 여부
    - `boolean safe`: 안전 구역 확정 여부
    - `boolean possiblePit` / `boolean definitePit`: 구덩이 후보 / 확정 여부
    - `boolean possibleWumpus` / `boolean definiteWumpus`: 웜파스 후보 / 확정 여부

* **`KnowledgeSummaryResponse.java` [MODIFY]**
  - **경로:** `src/main/java/com/wumpusslayers/wumpusworld/reasoning/dto/response/KnowledgeSummaryResponse.java`
  - **변경사항:**기존 `safePositions` 및 `visitedPositions` 리스트 외에, 16개 셀의 종합 추론 정보를 배열 형태로 프론트엔드에 한 번에 제공할 수 있도록 **`List<KbCellDetail> cellDetails`** 필드를 추가했습니다.

* **`ReasoningService.java` [MODIFY]**
  - **경로:** `src/main/java/com/wumpusslayers/wumpusworld/reasoning/service/ReasoningService.java`
  - **변경사항:** `getKnowledgeSummary()` 메서드를 전면 수정하여, 4x4 격자 전역(`1,1`부터 `4,4`까지)을 반복 순회하며 `KnowledgeBase`를 조회한 후 `KbCellDetail` 리스트를 구성하고 `KnowledgeSummaryResponse`에 적재하도록 매핑 로직을 이식했습니다.

---

### 📂 2) Game Engine 및 Pathfinder 추천 로직 개편
> **목적:** 시뮬레이션의 행동 결정 피드백을 직관화하고 AI 에이전트가 어떤 판단 근거(우선순위)로 추천 타일을 골랐는지 데이터로 포함시킵니다.

* **`GameEngine.java` [MODIFY]**
  - **경로:** `src/main/java/com/wumpusslayers/wumpusworld/simulation/service/GameEngine.java`
  - **변경사항:**
    1. **`KnowledgeUpdateService` 주입:** PathFinder가 다음 셀을 선택할 때 실시간 지식 베이스(KB) 정보를 검사할 수 있도록 의존성을 추가했습니다.
    2. **추천 순위 상세화 (`getPathFinderRecommendationMessage`):** PathFinder가 고른 추천 셀에 대해 지식 베이스를 대조하여 아래의 4가지 우선순위 텍스트를 응답 메시지에 동적으로 접미사로 붙여줍니다 (` | [PathFinder] ...` 포맷).
       - **1순위:** `안전한 미방문 칸`
       - **2순위:** `가장 가까운 Safe 미방문 칸으로 BFS 복귀`
       - **3순위 모험:** `미지의 미방문 칸`
       - **4순위 모험:** `Pit/Wumpus 후보 진입`
    3. **자동 실행 액션 프리픽스 보강 (`[Auto]`):** `/api/game/auto` 엔드포인트를 통해 에이전트가 스스로 행동을 결정할 경우, 프론트엔드가 수동 조작과 자동 시뮬레이션 로그를 명확히 분리할 수 있도록 응답 메시지 앞에 **`[Auto] 결정된 액션: <ACTION_NAME> | `** 프리픽스를 자동 적용하여 리턴하도록 갱신했습니다.

---

### 📂 3) Action Planner 결과 메시지 정렬
> **목적:** 사망 이벤트 및 웜파스 명중 등 핵심 게임 시나리오 발생 시 프론트엔드가 즉시 알아챌 수 있는 키워드 및 이벤트 태그를 표준화했습니다.

* **`ActionPlannerService.java` [MODIFY]**
  - **경로:** `src/main/java/com/wumpusslayers/wumpusworld/agent/service/ActionPlannerService.java`
  - **변경사항:**
    - 에이전트가 Pit에 빠지거나 Wumpus에게 사망하는 시나리오에서 반환하는 결과 메시지 맨 앞에 **`⚠️ [EVENT]`** 태그를 강제 이식했습니다. (프론트엔드 터미널에서 빨간색 사망 배지를 띄우는 기준으로 사용됩니다.)
    - 화살 사격 성공 시 비명 소리가 들리는 상황에서 메시지 내부에 **`[SCREAM]`** 키워드가 포함되도록 조율했습니다. (몬스터 사망 모달 팝업 트리거로 활용됩니다.)

---

### 📂 4) JUnit 테스트 코드 정합성 유지
> **목적:** DTO 필드 변경 및 `GameEngine` 생성자의 의존성 추가로 인해 기존 테스트가 깨지는 현상을 차단하고, 빌드가 성공하도록 리팩토링을 완료했습니다.

* **`GameControllerReasoningWebMvcTest.java` [MODIFY]**
  - 신규 추가된 `KbCellDetail` 필드를 Mocking 응답 객체에 빈 리스트(`List.of()`)로 주입하여 WebMvc 테스트 Assertions가 통과하도록 조치했습니다.
* **`GameEngineKnowledgePersistenceAfterDeathTest.java` [MODIFY]**
  - `GameEngine`의 새 의존성 규격에 맞춰 테스트 픽스처 셋업 시 `knowledgeUpdateService`를 생성자 아규먼트에 추가하여 빌드 컴파일을 정상화시켰습니다.

---

## 3. API 응답 및 통신 데이터 포맷 예시 (Payload Examples)

팀원들이 백엔드와 연동을 테스트할 때 참고할 수 있는 REST 응답 구조 예시입니다.

### 📡 1) GET `/api/game/reasoning/summary` (KB 추론 상태)
* **응답 예시:**
```json
{
  "safePositions": [
    {"x": 1, "y": 1},
    {"x": 1, "y": 2},
    {"x": 2, "y": 1}
  ],
  "visitedPositions": [
    {"x": 1, "y": 1},
    {"x": 2, "y": 1}
  ],
  "wumpusAlive": true,
  "heardScream": false,
  "cellDetails": [
    {
      "x": 1,
      "y": 1,
      "visited": true,
      "safe": true,
      "possiblePit": false,
      "definitePit": false,
      "possibleWumpus": false,
      "definiteWumpus": false
    },
    {
      "x": 2,
      "y": 2,
      "visited": false,
      "safe": false,
      "possiblePit": true,
      "definitePit": false,
      "possibleWumpus": true,
      "definiteWumpus": false
    },
    {
      "x": 3,
      "y": 2,
      "visited": false,
      "safe": false,
      "possiblePit": false,
      "definitePit": true,
      "possibleWumpus": false,
      "definiteWumpus": false
    }
    // ... 총 16개의 셀 상세 정보 반환
  ]
}
```

### 📡 2) POST `/api/game/auto` (자동 액션 수행)
* **응답 예시 (이동):**
```json
{
  "percept": {
    "breeze": true,
    "stench": false,
    "glitter": false,
    "bump": false,
    "scream": false
  },
  "isGameOver": false,
  "message": "[Auto] 결정된 액션: GO_FORWARD | 전진하여 (2,1) 위치로 이동했습니다. | [PathFinder] 다음 추천 셀: Position(x=2, y=2) [4순위 모험: Pit/Wumpus 후보 진입]",
  "actionPosition": {"x": 2, "y": 1},
  "diedInPit": false,
  "diedInWumpus": false
}
```

* **응답 예시 (몬스터 사격 시):**
```json
{
  "percept": {
    "breeze": false,
    "stench": false,
    "glitter": false,
    "bump": false,
    "scream": true
  },
  "isGameOver": false,
  "message": "[Auto] 결정된 액션: SHOOT | 화살을 쏘았습니다. 먼 곳에서 처절한 비명 소리가 들려옵니다! [SCREAM]",
  "actionPosition": {"x": 2, "y": 1},
  "diedInPit": false,
  "diedInWumpus": false
}
```

---

## 4. 로컬 빌드 및 기동 명령어

팀원들이 로컬에서 백엔드 환경을 올려 테스트할 수 있도록 다음의 그레이들 래퍼 명령어를 사용합니다.

```bash
# 1. 전체 JUnit 테스트 컴파일 및 수행 (성공 확인 필수)
./gradlew test

# 2. 로컬 Spring Boot 서버 기동 (8080 포트)
./gradlew bootRun
```
백엔드 기동 이후 프론트엔드(`Wumpus-World-Client/dist/index.html`) 파일을 브라우저로 실행하면 CORS 예외 없이 즉각 실시간 시뮬레이터가 유기적으로 맞물려 실행됩니다.
