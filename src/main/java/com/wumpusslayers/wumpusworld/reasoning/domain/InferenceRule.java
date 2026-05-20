package com.wumpusslayers.wumpusworld.reasoning.domain;

/**
 * 전진 추론에 사용할 규칙 식별자. 적용 순서는 {@link #defaultPriority()} 오름차순을 권장하며,
 * 실제 루프·종료 조건은 {@link com.wumpusslayers.wumpusworld.reasoning.service.RuleEngineService}를 본다(#13·#19).
 */
public enum InferenceRule {

    /** 방문한 칸에 Breeze가 없으면, 인접한 미방문 칸에서 pit 가능성 제거 */
    NO_BREEZE_CLEAR_ADJACENT_PIT_CANDIDATES(10),

    /** 방문한 칸에 Stench가 없으면, 인접한 미방문 칸에서 wumpus 가능성 제거 */
    NO_STENCH_CLEAR_ADJACENT_WUMPUS_CANDIDATES(20),

    /** 방문한 칸에 Breeze가 있으면, 인접 칸에 pit 후보 표시(#13) */
    BREEZE_MARK_PIT_CANDIDATES(30),

    /**
     * Breeze 칸의 인접 중 pit 후보가 안전 밖에서 하나뿐이면, 같은 인접의 나머지 칸은 pit 후보에서 제외(#19).
     * 다중 pit 환경에서도 “그 breeze를 설명할 수 있는 인접”만 좁히는 보수적 규칙이다.
     */
    BREEZE_PIT_SINGLETON_NARROWS_NEIGHBORS(35),

    /** 방문한 칸에 Stench가 있으면, 인접 칸에 wumpus 후보 표시(#13) */
    STENCH_MARK_WUMPUS_CANDIDATES(40),

    /**
     * Stench 칸의 인접 중 wumpus 후보가 안전 밖에서 하나뿐이면, 같은 인접의 나머지 칸은 wumpus 후보에서 제외(#19).
     * 다중 움퍼스 환경에서도 “그 stench를 설명할 수 있는 인접”만 좁히는 보수적 규칙이다.
     */
    STENCH_WUMPUS_SINGLETON_NARROWS_NEIGHBORS(45),

    /**
     * {@link KnowledgeBase#isWumpusAlive()} 가 false일 때 전 격자에서 움퍼스 후보를 제거한다.
     * 비명(percept)만으로 생존 플래그가 바뀌지 않으므로, 다중 움퍼스에서는 시뮬 등이 플래그를 맞춘 뒤 이 규칙이 동작한다(#13).
     * 식별자명은 관례상 남아 있으며 동작은 {@code isWumpusAlive} 기준이다.
     */
    SCREAM_WUMPUS_ELIMINATED(50),

    /**
     * {@link KnowledgeBase#isDefinitePit(com.wumpusslayers.wumpusworld.environment.domain.Position)} 인 칸은
     * 움퍼스가 있을 수 없으므로 {@code possibleWumpus}를 false로 만든다(#34, 상호 배제).
     */
    CONFIRMED_PIT_CLEARS_WUMPUS_CANDIDATE(60),

    /**
     * {@link KnowledgeBase#isDefiniteWumpus(com.wumpusslayers.wumpusworld.environment.domain.Position)} 인 칸은
     * Pit이 있을 수 없으므로 {@code possiblePit}을 false로 만든다(#37, 상호 배제).
     */
    CONFIRMED_WUMPUS_CLEARS_PIT_CANDIDATE(70);

    private final int defaultPriority;

    /** 규칙별 기본 우선순위(낮을수록 먼저 적용하는 식으로 엔진에서 사용, #13·#19). */
    InferenceRule(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    /** 기본 우선순위 값을 반환한다. */
    public int defaultPriority() {
        return defaultPriority;
    }
}
