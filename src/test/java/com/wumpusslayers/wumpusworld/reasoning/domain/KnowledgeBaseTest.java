package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeBaseTest {

    @Test
    @DisplayName("(1,1)은 안전이며 pit/wumpus 후보가 아니고, 나머지 칸은 후보로 시작한다.")
    void initialBeliefMatchesStartCellAssumptions() {
        KnowledgeBase kb = new KnowledgeBase();

        assertTrue(kb.isSafe(new Position(1, 1)));
        assertFalse(kb.isPossiblePit(new Position(1, 1)));
        assertFalse(kb.isPossibleWumpus(new Position(1, 1)));

        assertTrue(kb.isPossiblePit(new Position(4, 4)));
        assertTrue(kb.isPossibleWumpus(new Position(2, 3)));
        assertFalse(kb.isVisited(new Position(2, 2)));
        assertTrue(kb.isWumpusAlive());
        assertFalse(kb.isHeardScream());
    }

    @Test
    @DisplayName("clear 후 초기 상태로 돌아간다.")
    void clearResetsState() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.recordCellObservation(new Position(1, 1), Percept.builder()
                .stench(false).breeze(false).glitter(false).bump(false).scream(false).build());
        kb.markDefinitelySafe(new Position(2, 1));

        kb.clear();

        assertFalse(kb.isVisited(new Position(1, 1)));
        assertTrue(kb.isSafe(new Position(1, 1)));
        assertTrue(kb.isWumpusAlive());
        assertFalse(kb.isHeardScream());
    }

    @Test
    @DisplayName("관측 기록 시 방문 및 스냅샷이 저장된다.")
    void recordObservationStoresVisitAndPercept() {
        KnowledgeBase kb = new KnowledgeBase();
        Percept p = Percept.builder()
                .stench(true).breeze(false).glitter(false).bump(false).scream(false)
                .build();

        kb.recordCellObservation(new Position(2, 2), p);

        assertTrue(kb.isVisited(new Position(2, 2)));
        assertEquals(p, kb.getCellBelief(new Position(2, 2)).lastPercept());
    }

    @Test
    @DisplayName("Scream이 포함된 관측은 heardScream만 켜고, wumpusAlive는 시뮬이 끈다.")
    void screamObservationSetsHeardScreamOnly() {
        KnowledgeBase kb = new KnowledgeBase();
        Percept p = Percept.builder()
                .stench(false).breeze(false).glitter(false).bump(false).scream(true)
                .build();

        kb.recordCellObservation(new Position(1, 2), p);

        assertTrue(kb.isWumpusAlive());
        assertTrue(kb.isHeardScream());
    }

    @Test
    @DisplayName("좌표가 범위 밖이면 SimulationException이 발생한다.")
    void invalidPositionThrows() {
        KnowledgeBase kb = new KnowledgeBase();
        assertThrows(SimulationException.class, () -> kb.isSafe(new Position(0, 1)));
        assertThrows(SimulationException.class, () -> kb.recordCellObservation(new Position(1, 5), Percept.builder()
                .stench(false).breeze(false).glitter(false).bump(false).scream(false).build()));
    }

    @Test
    @DisplayName("안전 칸에 pit 후보를 true로 두면 불변식 위반으로 예외가 난다.")
    void cannotMarkPossiblePitOnSafeCell() {
        KnowledgeBase kb = new KnowledgeBase();
        assertThrows(SimulationException.class, () -> kb.setPossiblePit(new Position(1, 1), true));
    }

    @Test
    @DisplayName("copySafeGrid는 원본과 분리된 복사본을 반환한다.")
    void copyGridsAreIndependent() {
        KnowledgeBase kb = new KnowledgeBase();
        boolean[][] copy = kb.copySafeGrid();
        copy[0][0] = false;
        assertTrue(kb.isSafe(new Position(1, 1)));
    }

    @Test
    @DisplayName("markDefinitePit은 definitePit=true, possibleWumpus=false로 만든다(#34).")
    void markDefinitePitSetsFlagsConsistently() {
        KnowledgeBase kb = new KnowledgeBase();
        Position p = new Position(3, 3);

        kb.markDefinitePit(p);

        assertTrue(kb.isDefinitePit(p));
        assertTrue(kb.isPossiblePit(p));
        assertFalse(kb.isPossibleWumpus(p));
        assertFalse(kb.isSafe(p));
    }

    @Test
    @DisplayName("안전 칸을 markDefinitePit 하면 SimulationException이다.")
    void markDefinitePitOnSafeCellThrows() {
        KnowledgeBase kb = new KnowledgeBase();
        assertThrows(SimulationException.class, () -> kb.markDefinitePit(new Position(1, 1)));
    }

    @Test
    @DisplayName("Pit 확정 칸을 markDefinitelySafe 하면 SimulationException이다.")
    void markDefinitelySafeOnDefinitePitThrows() {
        KnowledgeBase kb = new KnowledgeBase();
        Position p = new Position(2, 3);
        kb.markDefinitePit(p);
        assertThrows(SimulationException.class, () -> kb.markDefinitelySafe(p));
    }

    @Test
    @DisplayName("clear 후 definitePit도 false로 돌아간다.")
    void clearResetsDefinitePit() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.markDefinitePit(new Position(2, 2));
        kb.clear();
        assertFalse(kb.isDefinitePit(new Position(2, 2)));
    }
}
