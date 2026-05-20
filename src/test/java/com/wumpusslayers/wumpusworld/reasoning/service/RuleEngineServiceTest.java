package com.wumpusslayers.wumpusworld.reasoning.service;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.InferenceRule;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineServiceTest {

    private final RuleEngineService engine = new RuleEngineService();

    private static Percept p(boolean stench, boolean breeze) {
        return Percept.builder()
                .stench(stench)
                .breeze(breeze)
                .glitter(false)
                .bump(false)
                .scream(false)
                .build();
    }

    @Test
    @DisplayName("(1,1) 무풍·무악취 관측 후 인접 칸이 안전으로 추론된다.")
    void noBreezeNoStenchAtStartInfersAdjacentSafe() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));

        engine.runInference(kb);

        assertTrue(kb.isSafe(new Position(1, 2)));
        assertTrue(kb.isSafe(new Position(2, 1)));
        assertFalse(kb.isPossiblePit(new Position(1, 2)));
        assertFalse(kb.isPossibleWumpus(new Position(1, 2)));
    }

    @Test
    @DisplayName("(2,1)에서 breeze면 안전한 인접은 pit 후보에서 제외되고, 미방문 인접만 pit 후보가 된다.")
    void breezeMarksUnvisitedNeighborsAsPossiblePitOnly() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(false, true));

        engine.runInference(kb);

        assertTrue(kb.isSafe(new Position(1, 1)));
        assertTrue(kb.isSafe(new Position(2, 1)));
        assertTrue(kb.isPossiblePit(new Position(2, 2)));
        assertTrue(kb.isPossiblePit(new Position(3, 1)));
        assertFalse(kb.isPossiblePit(new Position(1, 1)));
    }

    @Test
    @DisplayName("비명만으로는 전 격자 움퍼스 후보가 사라지지 않는다. 전멸 시에는 setWumpusAlive(false) 후 규칙이 비운다.")
    void screamAloneDoesNotClearWumpusCandidatesUntilGloballyDead() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(true, false));
        engine.runInference(kb);
        assertTrue(kb.isPossibleWumpus(new Position(1, 2)) || kb.isPossibleWumpus(new Position(2, 1)));

        kb.recordCellObservation(new Position(1, 2), Percept.builder()
                .stench(false).breeze(false).glitter(false).bump(false).scream(true)
                .build());

        engine.runInference(kb);

        assertTrue(kb.isWumpusAlive());
        assertTrue(kb.isHeardScream());
        assertTrue(kb.isPossibleWumpus(new Position(1, 2)) || kb.isPossibleWumpus(new Position(2, 1)));

        kb.setWumpusAlive(false);
        engine.runInference(kb);

        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                assertFalse(kb.isPossibleWumpus(new Position(x, y)), "cell " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("KnowledgeBase가 null이면 SimulationException이다.")
    void nullKnowledgeBaseRejected() {
        assertThrows(SimulationException.class, () -> engine.runInference(null));
    }

    @Test
    @DisplayName("InferenceRule 기본 우선순위 순서가 문서·엔진 적용 순과 일치한다.")
    void inferenceRuleDefaultOrder() {
        var order = RuleEngineService.rulesInDefaultOrder();
        assertEquals(InferenceRule.NO_BREEZE_CLEAR_ADJACENT_PIT_CANDIDATES, order.get(0));
        assertEquals(InferenceRule.NO_STENCH_CLEAR_ADJACENT_WUMPUS_CANDIDATES, order.get(1));
        assertEquals(InferenceRule.BREEZE_MARK_PIT_CANDIDATES, order.get(2));
        assertEquals(InferenceRule.BREEZE_PIT_SINGLETON_NARROWS_NEIGHBORS, order.get(3));
        assertEquals(InferenceRule.STENCH_MARK_WUMPUS_CANDIDATES, order.get(4));
        assertEquals(InferenceRule.STENCH_WUMPUS_SINGLETON_NARROWS_NEIGHBORS, order.get(5));
        assertEquals(InferenceRule.SCREAM_WUMPUS_ELIMINATED, order.get(6));
        assertEquals(InferenceRule.CONFIRMED_PIT_CLEARS_WUMPUS_CANDIDATE, order.get(7));
    }

    @Test
    @DisplayName("Pit 확정 칸은 추론 후 possibleWumpus가 false다(#34).")
    void definitePitClearsWumpusCandidate() {
        KnowledgeBase kb = new KnowledgeBase();
        Position pit = new Position(3, 3);
        kb.markDefinitePit(pit);
        engine.runInference(kb);
        assertFalse(kb.isPossibleWumpus(pit));
        assertTrue(kb.isPossiblePit(pit));
    }

    @Test
    @DisplayName("stench 인접이 Pit 확정이면 wumpus 후보로 다시 켜지지 않는다(#34).")
    void stenchDoesNotReMarkDefinitePitAsWumpusCandidate() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(true, false));
        engine.runInference(kb);

        Position pit = new Position(3, 1);
        kb.markDefinitePit(pit);
        engine.runInference(kb);

        assertTrue(kb.isDefinitePit(pit));
        assertFalse(kb.isPossibleWumpus(pit));
    }

    @Test
    @DisplayName("국소 stench만으로 먼 칸의 움퍼스 후보를 없애지 않는다(다중 움퍼스 가능).")
    void localStenchDoesNotClearDistantWumpusCandidates() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(2, 1), p(true, false));
        engine.runInference(kb);

        assertTrue(kb.isPossibleWumpus(new Position(4, 4)),
                "멀리 또 다른 움퍼스가 있을 수 있으므로 교집합식 전역 축소는 하지 않는다");
    }

    @Test
    @DisplayName("breeze 인접 pit 후보가 하나로 좁혀지면 나머지 인접 pit 후보가 제거된다(#19).")
    void breezeSingletonNarrowsAdjacentPitCandidates() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(2, 1), p(false, true));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(3, 1), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isPossiblePit(new Position(2, 2)));
        assertFalse(kb.isPossiblePit(new Position(3, 1)));
    }

    @Test
    @DisplayName("stench 인접 wumpus 후보가 하나로 좁혀지면 나머지 인접 wumpus 후보가 제거된다(#19).")
    void stenchSingletonNarrowsAdjacentWumpusCandidates() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(2, 1), p(true, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(3, 1), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isPossibleWumpus(new Position(2, 2)));
        assertFalse(kb.isPossibleWumpus(new Position(3, 1)));
    }
}
