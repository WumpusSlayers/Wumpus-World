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
     * 시뮬이 알려 준 “격자에 살아 있는 움퍼스가 하나라도 있는지”를 KB {@code wumpusAlive}에 반영하고 추론을 한 번 돌린다.
     * 호출 측(예: {@link com.wumpusslayers.wumpusworld.simulation.service.GameEngine})이 {@code World} 등에서 값을 계산해 넘긴다(#15·다중 움퍼스).
     */
    public void syncWumpusAlive(String sessionId, boolean anyLiveWumpusOnGrid) {
        requireSessionId(sessionId);
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) {
            return;
        }
        kb.setWumpusAlive(anyLiveWumpusOnGrid);
        ruleEngineService.runInference(kb);
    }

    /**
     * Pit으로 사망한 좌표를 KB {@code definitePit}에 반영하고 추론을 한 번 돌린다(#34).
     * {@link com.wumpusslayers.wumpusworld.simulation.service.GameEngine}에서 관측 반영 전에 호출한다.
     */
    public void markAgentDiedInPit(String sessionId, Position position) {
        requireSessionId(sessionId);
        if (position == null) {
            return;
        }
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) {
            return;
        }
        kb.markDefinitePit(position);
        ruleEngineService.runInference(kb);
    }

    /**
     * Wumpus에 사망한 좌표를 KB {@code definiteWumpus}에 반영하고 추론을 한 번 돌린다(#37).
     * {@link com.wumpusslayers.wumpusworld.simulation.service.GameEngine}에서 관측 반영 전에 호출한다.
     */
    public void markAgentDiedInWumpus(String sessionId, Position position) {
        requireSessionId(sessionId);
        if (position == null) {
            return;
        }
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) {
            return;
        }
        kb.markDefiniteWumpus(position);
        ruleEngineService.runInference(kb);
    }

    /**
     * 화살이 빗나간 경로상 칸들의 Wumpus 후보를 제거하고,
     * 인접에 breeze 감지 칸이 없으면 safe로 확정한다.
     */
    public void markArrowMissPath(String sessionId, List<Position> path, KnowledgeBase kb) {
        requireSessionId(sessionId);
        if (path == null || path.isEmpty() || kb == null) {
            return;
        }
        for (Position pos : path) {
            if (kb.isDefiniteWumpus(pos)) continue;
            System.out.println("[DEBUG] 처리 중: " + pos + " | possibleWumpus: " + kb.isPossibleWumpus(pos));
            /** 화살 통과 칸 영구 표시 — stench 규칙에서 재등록 방지 */
            kb.setArrowCleared(pos);
            /** 화살 경로상 Wumpus 후보 제거 */
            if (kb.isPossibleWumpus(pos)) {
                kb.setPossibleWumpus(pos, false);
            }
        }
        ruleEngineService.runInference(kb);
    }

    /**
     * 현재 KB에 대해 규칙 엔진만 다시 적용한다. KB가 없으면 아무 것도 하지 않는다.
     */
    public void runInference(String sessionId) {
        requireSessionId(sessionId);
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb != null) {
            ruleEngineService.runInference(kb);
            kb.printState(); // 추론 완료 후 현재 KB 상태 시각화 출력
        }
    }

    /**
     * 완전히 새 게임을 시작하기 직전에 해당 세션의 KB를 제거한다.
     * 사망 후 (1,1) 복귀에서는 호출하지 않는다(#15).
     */
    public void resetKnowledgeForNewGame(String sessionId) {
        requireSessionId(sessionId);
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
