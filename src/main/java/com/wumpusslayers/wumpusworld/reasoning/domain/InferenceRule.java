package com.wumpusslayers.wumpusworld.reasoning.domain;

/**
 * 전진 추론에 사용할 규칙 식별자(뼈대). 실제 적용 로직은 {@code RuleEngineService}(#13)에서 구현한다.
 */
public enum InferenceRule {

    /** 방문한 칸에 Breeze가 없으면, 인접한 미방문 칸에서 pit 가능성 제거 */
    NO_BREEZE_CLEAR_ADJACENT_PIT_CANDIDATES(10),

    /** 방문한 칸에 Stench가 없으면, 인접한 미방문 칸에서 wumpus 가능성 제거 */
    NO_STENCH_CLEAR_ADJACENT_WUMPUS_CANDIDATES(20),

    /** 방문한 칸에 Breeze가 있으면, 인접 미방문 칸들에 pit 후보 표시(교집합·축소는 #13) */
    BREEZE_MARK_PIT_CANDIDATES(30),

    /** 방문한 칸에 Stench가 있으면, 인접 미방문 칸들에 wumpus 후보 표시(교집합·축소는 #13) */
    STENCH_MARK_WUMPUS_CANDIDATES(40),

    /** Scream 관측 시 움퍼스 사망 및 후보 정리(#13에서 세부 처리) */
    SCREAM_WUMPUS_ELIMINATED(50);

    private final int defaultPriority;

    InferenceRule(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public int defaultPriority() {
        return defaultPriority;
    }
}
