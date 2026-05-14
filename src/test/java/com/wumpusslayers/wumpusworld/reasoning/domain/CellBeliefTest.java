package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellBeliefTest {

    @Test
    @DisplayName("unseen 상태는 방문하지 않았고 percept가 없다.")
    void unseen() {
        CellBelief b = CellBelief.unseen();
        assertFalse(b.visited());
        assertNull(b.lastPercept());
    }

    @Test
    @DisplayName("withObservation은 새 불변 인스턴스를 만든다.")
    void withObservationReturnsNewInstance() {
        CellBelief unseen = CellBelief.unseen();
        Percept p = Percept.builder()
                .stench(false).breeze(true).glitter(false).bump(false).scream(false)
                .build();
        CellBelief seen = unseen.withObservation(p);

        assertFalse(unseen.visited());
        assertTrue(seen.visited());
        assertEquals(p, seen.lastPercept());
    }

    @Test
    @DisplayName("visited인데 percept가 null이면 record 생성 시 예외")
    void invalidCombinationRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CellBelief(true, null));
        assertThrows(IllegalArgumentException.class, () -> new CellBelief(false, Percept.builder()
                .stench(false).breeze(false).glitter(false).bump(false).scream(false).build()));
    }
}
