package com.wumpusslayers.wumpusworld.agent.service;

import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import com.wumpusslayers.wumpusworld.reasoning.service.KnowledgeUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor

public class PathFinderService {

    private final KnowledgeUpdateService knowledgeUpdateService;

    /**
     * 우선순위 기반으로 다음 이동할 셀을 선택한다.
     * 1순위: Safe && 미방문 → 안전하게 새로운 칸 탐색
     * 2순위: Safe && 방문함 → 갔던 곳으로 backtrack
     * 3순위: Pit 후보 >= 2개인 셀 → 우회 불가 시 공격적 진입
     * 4순위: Unknown → 정보 없는 칸 공격적 진입
     * Pit 확정 / Wumpus 확정 셀은 탐색 대상에서 영구 제외
     */
    public Position selectNextCell(String sessionId, Position current) {
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) return null;

        List<Position> neighbors = getNeighbors(current);

        // 1순위: Safe && 미방문
        for (Position pos : neighbors) {
            if (isValid(pos) && kb.isSafe(pos) && !kb.isVisited(pos)) {
                return pos;
            }
        }

        // 2순위: Safe && 방문함 (backtrack)
        for (Position pos : neighbors) {
            if (isValid(pos) && kb.isSafe(pos) && kb.isVisited(pos)) {
                boolean hasUnexplored = getNeighbors(pos).stream()
                        .anyMatch(n -> isValid(n) && kb.isSafe(n) && !kb.isVisited(n));
                if (hasUnexplored) {
                    System.out.println("[PathFinder] 2순위 선택 (미탐색 인접 있는 방문 칸): " + pos);
                    return pos;
                }
            }
        }

        // 3순위: Pit 후보 >= 2개인 셀 (공격적 진입)
        for (Position pos : neighbors) {
            if (isValid(pos) && !kb.isDefinitePit(pos) && !kb.isDefiniteWumpus(pos)
                    && kb.isPossiblePit(pos)) {
                return pos;
            }
        }

        // 4순위: Unknown (공격적 진입)
        // Reasoning에서 safe 처리가 안 된 미지의 칸으로 진입
        // 현재 로직상 거의 발생하지 않으나, Safe 칸이 완전히 소진된 경우 대비
        for (Position pos : neighbors) {
            if (isValid(pos) && !kb.isDefinitePit(pos) && !kb.isDefiniteWumpus(pos)) {
                return pos;
            }
        }

        return null;
    }

    /**
     * 인접한 4방향 셀 목록을 반환한다. (상하좌우)
     */
    private List<Position> getNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<>();
        neighbors.add(new Position(pos.getX() + 1, pos.getY()));
        neighbors.add(new Position(pos.getX() - 1, pos.getY()));
        neighbors.add(new Position(pos.getX(), pos.getY() + 1));
        neighbors.add(new Position(pos.getX(), pos.getY() - 1));
        return neighbors;
    }

    /**
     * 해당 위치가 4x4 그리드 범위 내인지 확인한다.
     */
    private boolean isValid(Position pos) {
        return pos.getX() >= 1 && pos.getX() <= 4
                && pos.getY() >= 1 && pos.getY() <= 4;
    }
}
