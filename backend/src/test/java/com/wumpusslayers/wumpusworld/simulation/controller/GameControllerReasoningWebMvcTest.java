package com.wumpusslayers.wumpusworld.simulation.controller;

import com.wumpusslayers.wumpusworld.reasoning.dto.response.KnowledgeSummaryResponse;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.PositionCoordinate;
import com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService;
import com.wumpusslayers.wumpusworld.simulation.service.GameEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameControllerReasoningWebMvcTest {

    @Mock
    private GameEngine gameEngine;

    @Mock
    private ReasoningService reasoningService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GameController controller = new GameController(gameEngine, reasoningService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/game/reasoning/summary 가 JSON으로 요약을 반환한다.")
    void reasoningSummaryEndpoint() throws Exception {
        when(reasoningService.getKnowledgeSummary("tester")).thenReturn(new KnowledgeSummaryResponse(
                List.of(new PositionCoordinate(1, 1)),
                List.of(new PositionCoordinate(1, 1)),
                true,
                false,
                List.of()
        ));

        mockMvc.perform(get("/api/game/reasoning/summary").param("userId", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wumpusAlive").value(true))
                .andExpect(jsonPath("$.heardScream").value(false))
                .andExpect(jsonPath("$.safeCells[0].x").value(1))
                .andExpect(jsonPath("$.safeCells[0].y").value(1));
    }
}
