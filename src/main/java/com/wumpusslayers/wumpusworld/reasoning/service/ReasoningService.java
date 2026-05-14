package com.wumpusslayers.wumpusworld.reasoning.service;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.KnowledgeSummaryResponse;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.PositionCoordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 세션별 KB에 관측을 반영하고 추론을 실행하며, 질의 결과를 DTO로 돌려준다(#14).
 * {@link KnowledgeUpdateService}·{@link RuleEngineService}를 조합한다.
 */
@Service
@RequiredArgsConstructor
public class ReasoningService {

    private final KnowledgeUpdateService knowledgeUpdateService;
    private final RuleEngineService ruleEngineService;

    /**
     * 관측을 KB에 기록한 뒤 전진 추론을 한 번 돌린다(#12·#13).
     */
    public void updateFromObservation(String sessionId, Position position, Percept percept) {
        requireSessionId(sessionId);
        knowledgeUpdateService.observe(sessionId, position, percept);
        runInference(sessionId);
    }

    /**
     * 현재 KB에 대해 규칙 엔진만 다시 적용한다. KB가 없으면 아무 것도 하지 않는다.
     */
    public void runInference(String sessionId) {
        requireSessionId(sessionId);
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb != null) {
            ruleEngineService.runInference(kb);
        }
    }

    /**
     * 완전히 새 게임을 시작하기 직전에 해당 세션의 KB를 제거한다.
     * 사망 후 (1,1) 복귀에서는 호출하지 않는다(#15).
     */
    public void resetKnowledgeForNewGame(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        knowledgeUpdateService.resetSession(sessionId);
    }

    /**
     * 안전·방문 칸 목록과 전역 움퍼스 플래그를 반환한다. KB가 없으면 {@link KnowledgeSummaryResponse#empty()}.
     */
    public KnowledgeSummaryResponse getKnowledgeSummary(String sessionId) {
        requireSessionId(sessionId);
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) {
            return KnowledgeSummaryResponse.empty();
        }
        List<PositionCoordinate> safe = new ArrayList<>();
        List<PositionCoordinate> visited = new ArrayList<>();
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (kb.isSafe(p)) {
                    safe.add(new PositionCoordinate(x, y));
                }
                if (kb.isVisited(p)) {
                    visited.add(new PositionCoordinate(x, y));
                }
            }
        }
        return new KnowledgeSummaryResponse(safe, visited, kb.isWumpusAlive(), kb.isHeardScream());
    }

    /** sessionId가 null·공백이면 {@link SimulationException}을 던진다. */
    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new SimulationException("sessionId must not be null or blank");
        }
    }
}
