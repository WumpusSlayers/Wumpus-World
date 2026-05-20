package com.wumpusslayers.wumpusworld.reasoning.service;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.PositionCoordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReasoningServiceTest {

    private final KnowledgeUpdateService knowledgeUpdateService = new KnowledgeUpdateService();
    private final RuleEngineService ruleEngineService = new RuleEngineService();
    private final ReasoningService reasoningService = new ReasoningService(knowledgeUpdateService, ruleEngineService);

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
    @DisplayName("updateFromObservation 후 요약에 (1,1) 방문·인접 안전이 반영된다.")
    void updateThenSummaryReflectsInference() {
        reasoningService.updateFromObservation("u1", new Position(1, 1), p(false, false));

        var summary = reasoningService.getKnowledgeSummary("u1");
        assertTrue(summary.visitedCells().contains(new PositionCoordinate(1, 1)));
        assertTrue(summary.safeCells().contains(new PositionCoordinate(1, 2)));
        assertTrue(summary.safeCells().contains(new PositionCoordinate(2, 1)));
        assertTrue(summary.wumpusAlive());
    }

    @Test
    @DisplayName("syncWumpusAlive(false)는 KB wumpusAlive를 false로 맞춘다.")
    void syncWumpusAliveReflectsSimulationFlag() {
        reasoningService.updateFromObservation("u1", new Position(1, 1), p(false, false));
        assertTrue(reasoningService.getKnowledgeSummary("u1").wumpusAlive());

        reasoningService.syncWumpusAlive("u1", false);

        assertFalse(reasoningService.getKnowledgeSummary("u1").wumpusAlive());
    }

    @Test
    @DisplayName("resetKnowledgeForNewGame 후에는 요약이 비어 있다.")
    void resetClearsSession() {
        reasoningService.updateFromObservation("u1", new Position(1, 1), p(false, false));
        reasoningService.resetKnowledgeForNewGame("u1");

        var summary = reasoningService.getKnowledgeSummary("u1");
        assertEquals(0, summary.visitedCells().size());
        assertEquals(0, summary.safeCells().size());
    }

    @Test
    @DisplayName("sessionId가 비어 있으면 SimulationException이다.")
    void blankSessionRejected() {
        assertThrows(SimulationException.class, () ->
                reasoningService.updateFromObservation("", new Position(1, 1), p(false, false)));
        assertThrows(SimulationException.class, () -> reasoningService.getKnowledgeSummary("  "));
    }

    @Test
    @DisplayName("markAgentDiedInPit는 definitePit을 켜고 possibleWumpus를 끈다(#34).")
    void markAgentDiedInPitUpdatesKnowledgeBase() {
        reasoningService.updateFromObservation("u1", new Position(1, 1), p(false, false));
        Position deathPos = new Position(2, 2);

        reasoningService.markAgentDiedInPit("u1", deathPos);

        var kb = knowledgeUpdateService.getKnowledgeBaseOrNull("u1");
        assertNotNull(kb);
        assertTrue(kb.isDefinitePit(deathPos));
        assertFalse(kb.isPossibleWumpus(deathPos));
    }

    @Test
    @DisplayName("markAgentDiedInPit(null)은 no-op이다.")
    void markAgentDiedInPitNullPositionIsNoOp() {
        reasoningService.updateFromObservation("u1", new Position(1, 1), p(false, false));
        var kb = knowledgeUpdateService.getKnowledgeBaseOrNull("u1");
        boolean[][] before = kb.copyDefinitePitGrid();
        reasoningService.markAgentDiedInPit("u1", null);
        assertArrayEquals(before, kb.copyDefinitePitGrid());
    }
}
