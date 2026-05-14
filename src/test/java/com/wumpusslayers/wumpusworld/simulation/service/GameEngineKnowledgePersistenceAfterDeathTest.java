package com.wumpusslayers.wumpusworld.simulation.service;

import com.wumpusslayers.wumpusworld.agent.service.ActionPlannerService;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.environment.service.PerceptService;
import com.wumpusslayers.wumpusworld.environment.service.WorldGeneratorService;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.PositionCoordinate;
import com.wumpusslayers.wumpusworld.reasoning.service.KnowledgeUpdateService;
import com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService;
import com.wumpusslayers.wumpusworld.reasoning.service.RuleEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEngineKnowledgePersistenceAfterDeathTest {

    @Mock
    private WorldGeneratorService worldGeneratorService;

    private ReasoningService reasoningService;
    private GameEngine gameEngine;

    @BeforeEach
    void setUp() {
        PerceptService perceptService = new PerceptService();
        ActionPlannerService actionPlannerService = new ActionPlannerService(perceptService);
        KnowledgeUpdateService knowledgeUpdateService = new KnowledgeUpdateService();
        reasoningService = new ReasoningService(knowledgeUpdateService, new RuleEngineService());
        gameEngine = new GameEngine(worldGeneratorService, actionPlannerService, perceptService, reasoningService);
    }

    @Test
    @DisplayName("구덩이 사망 후에도 사망 전에 관측된 방문 칸이 KB에 남는다(#15).")
    void knowledgeBaseSurvivesPitDeath() {
        World world = new World();
        world.getGrid().getCell(new Position(3, 1)).setHasPit(true);
        when(worldGeneratorService.generateWorld()).thenReturn(world);

        String userId = "death-test";
        gameEngine.startNewGame(userId);

        gameEngine.processAction(userId, ActionType.GO_FORWARD);
        assertEquals(new Position(2, 1), gameEngine.getGameStatus(userId).getAgentPosition());

        gameEngine.processAction(userId, ActionType.GO_FORWARD);
        assertEquals(new Position(1, 1), gameEngine.getGameStatus(userId).getAgentPosition());

        var summary = reasoningService.getKnowledgeSummary(userId);
        assertTrue(summary.visitedCells().contains(new PositionCoordinate(2, 1)),
                "(2,1)에 대한 관측이 사망 후에도 KB에 남아 있어야 한다");
    }
}
