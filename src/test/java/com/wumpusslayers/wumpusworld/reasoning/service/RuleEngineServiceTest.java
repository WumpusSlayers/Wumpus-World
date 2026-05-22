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
                Position cell = new Position(x, y);
                if (kb.isDefiniteWumpus(cell)) {
                    continue;
                }
                assertFalse(kb.isPossibleWumpus(cell), "cell " + x + "," + y);
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
        assertEquals(InferenceRule.BREEZE_UNIQUE_UNSAFE_NEIGHBOR_IDENTIFIES_PIT, order.get(4));
        assertEquals(InferenceRule.STENCH_MARK_WUMPUS_CANDIDATES, order.get(5));
        assertEquals(InferenceRule.STENCH_WUMPUS_SINGLETON_NARROWS_NEIGHBORS, order.get(6));
        assertEquals(InferenceRule.STENCH_UNIQUE_UNSAFE_NEIGHBOR_IDENTIFIES_WUMPUS, order.get(7));
        assertEquals(InferenceRule.SCREAM_WUMPUS_ELIMINATED, order.get(8));
        assertEquals(InferenceRule.CONFIRMED_PIT_CLEARS_WUMPUS_CANDIDATE, order.get(9));
        assertEquals(InferenceRule.DEFINITE_PIT_EXPLAINS_BREEZE, order.get(10));
        assertEquals(InferenceRule.DEFINITE_WUMPUS_EXPLAINS_STENCH, order.get(11));
        assertEquals(InferenceRule.CONFIRMED_WUMPUS_CLEARS_PIT_CANDIDATE, order.get(12));
    }

    @Test
    @DisplayName("(2,1) stench·인접 전부 safe면 (2,2)를 Wumpus 확정한다(#39).")
    void stenchWithOnlyOneUnsafeNeighborConfirmsWumpus() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(true, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(3, 1), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isDefiniteWumpus(new Position(2, 2)),
                "(2,1) stench + (1,2),(3,1) safe → 움퍼스는 (2,2)뿐");
    }

    @Test
    @DisplayName("무악취 방문 칸 이웃은 stench 칸이 wumpus 후보로 다시 켜지지 않는다(#39).")
    void noStenchAtAdjacentVisitedCellBlocksStenchReMark() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 2), p(true, true));
        engine.runInference(kb);
        assertTrue(kb.isPossibleWumpus(new Position(2, 3)));
        assertTrue(kb.isPossibleWumpus(new Position(3, 2)));

        kb.recordCellObservation(new Position(1, 3), p(false, true));
        engine.runInference(kb);

        assertFalse(kb.isPossibleWumpus(new Position(2, 3)),
                "(1,3) 무악취 → (2,3)에 움퍼스 없음; (2,2) stench가 다시 W를 켜면 안 됨");
        assertTrue(kb.isPossiblePit(new Position(2, 3)));
        assertTrue(kb.isPossibleWumpus(new Position(3, 2)));
    }

    @Test
    @DisplayName("(2,1) breeze·인접 전부 safe면 (2,2)를 Pit 확정한다(#39).")
    void breezeWithOnlyOneUnsafeNeighborConfirmsPit() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(false, true));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(3, 1), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isDefinitePit(new Position(2, 2)),
                "(2,1) breeze + (1,1),(3,1) safe → pit은 (2,2)뿐");
    }

    @Test
    @DisplayName("wumpusAlive=false면 STENCH_UNIQUE_UNSAFE는 새 wumpus 확정을 만들지 않는다(#39).")
    void stenchUniqueUnsafeIsNoOpWhenWumpusAliveFalse() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(true, false));
        kb.recordCellObservation(new Position(3, 1), p(false, false));

        kb.setWumpusAlive(false);
        engine.runInference(kb);

        assertFalse(kb.isDefiniteWumpus(new Position(2, 2)),
                "wumpus 전멸 상태면 인접 unsafe 1개여도 확정 룰이 돌지 않아야 한다");
    }

    @Test
    @DisplayName("다중 pit: 한쪽 breeze만 Pit 확정돼도 다른 이웃 pit 후보 칸은 safe가 되지 않는다(#39).")
    void multiPitDefiniteOnOneNeighborDoesNotClearOtherBreezeNeighbor() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(3, 3), p(false, true));
        engine.runInference(kb);
        assertTrue(kb.isPossiblePit(new Position(3, 4)));

        kb.markDefinitePit(new Position(4, 3));
        kb.recordCellObservation(new Position(4, 1), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isDefinitePit(new Position(4, 3)));
        assertFalse(kb.isSafe(new Position(3, 4)),
                "(3,3) breeze에 (3,4) pit 후보가 남아 있으면 (3,4)는 safe가 되면 안 됨");
    }

    @Test
    @DisplayName("Breeze 칸 인접 Pit 확정이면 나머지 pit 후보가 제거된다(#34·#39).")
    void definitePitExplainsBreezeClearsRemainingPitCandidates() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);
        kb.recordCellObservation(new Position(2, 1), p(false, true));
        engine.runInference(kb);
        assertTrue(kb.isPossiblePit(new Position(2, 2)));
        assertTrue(kb.isPossiblePit(new Position(3, 1)));

        kb.markDefinitePit(new Position(3, 1));
        kb.recordCellObservation(new Position(1, 2), p(false, false));
        engine.runInference(kb);

        assertTrue(kb.isDefinitePit(new Position(3, 1)));
        assertFalse(kb.isPossiblePit(new Position(2, 2)), "(3,1) Pit 확정으로 (2,1) breeze 설명됨");
        assertTrue(kb.isSafe(new Position(2, 2)) || !kb.isPossiblePit(new Position(2, 2)));
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
    @DisplayName("Wumpus 확정 칸은 추론 후에도 safe로 바뀌지 않는다(#37).")
    void definiteWumpusVisitedCellStaysUnsafe() {
        KnowledgeBase kb = new KnowledgeBase();
        Position wumpus = new Position(2, 3);

        // 시뮬은 사망 좌표에 관측을 기록한 뒤 markDefiniteWumpus를 호출함
        kb.recordCellObservation(wumpus, p(false, false));
        kb.markDefiniteWumpus(wumpus);

        engine.runInference(kb);

        assertTrue(kb.isDefiniteWumpus(wumpus));
        assertFalse(kb.isSafe(wumpus));
        assertFalse(kb.isPossiblePit(wumpus));
    }

    @Test
    @DisplayName("Wumpus 확정 칸은 추론 후 possiblePit가 false다(#37, 상호 배제).")
    void definiteWumpusClearsPitCandidate() {
        KnowledgeBase kb = new KnowledgeBase();
        Position wumpus = new Position(3, 3);

        kb.markDefiniteWumpus(wumpus);
        engine.runInference(kb);

        assertFalse(kb.isPossiblePit(wumpus));
        assertTrue(kb.isPossibleWumpus(wumpus));
    }

    @Test
    @DisplayName("wumpusAlive=false에서도 definiteWumpus 칸은 invariant를 안 깬다(#37).")
    void screamRuleRespectsDefiniteWumpus() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.markDefiniteWumpus(new Position(2, 3));

        kb.setWumpusAlive(false);
        assertDoesNotThrow(() -> engine.runInference(kb));

        assertTrue(kb.isPossibleWumpus(new Position(2, 3)));
        assertFalse(kb.isPossibleWumpus(new Position(4, 4)));
    }

    @Test
    @DisplayName("국소 stench는 인접만 wumpus 후보로 등록하고 먼 칸은 건드리지 않는다(#39).")
    void localStenchOnlyMarksAdjacentWumpusCandidates() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(2, 1), p(true, false));
        engine.runInference(kb);

        // (2,1) 인접 중 safe가 아닌 (2,2), (3,1)만 wumpus 후보로 등록된다
        assertTrue(kb.isPossibleWumpus(new Position(2, 2)));
        assertTrue(kb.isPossibleWumpus(new Position(3, 1)));
        // 신호와 무관한 먼 칸은 후보로 켜지지 않는다(다중 움퍼스가 거기 있을 수도 있지만, 근거가 없음)
        assertFalse(kb.isPossibleWumpus(new Position(4, 4)));
        assertFalse(kb.isPossibleWumpus(new Position(1, 4)));
    }

    @Test
    @DisplayName("breeze는 신호 칸의 인접만 pit 후보로 등록하고 먼 칸은 건드리지 않는다(#39).")
    void breezeRegistersPitCandidatesOnAdjacentOnly() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), p(false, false));
        engine.runInference(kb);

        kb.recordCellObservation(new Position(2, 1), p(false, true));
        engine.runInference(kb);

        assertTrue(kb.isPossiblePit(new Position(2, 2)));
        assertTrue(kb.isPossiblePit(new Position(3, 1)));
        assertFalse(kb.isPossiblePit(new Position(4, 4)));
        assertFalse(kb.isPossiblePit(new Position(1, 4)));
    }

    @Test
    @DisplayName("관측 전에는 어떤 칸도 pit/wumpus 후보로 등록되지 않는다(#39).")
    void noObservationLeavesAllCandidatesEmpty() {
        KnowledgeBase kb = new KnowledgeBase();

        engine.runInference(kb);

        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position pos = new Position(x, y);
                assertFalse(kb.isPossiblePit(pos), "possiblePit at " + pos);
                assertFalse(kb.isPossibleWumpus(pos), "possibleWumpus at " + pos);
            }
        }
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
