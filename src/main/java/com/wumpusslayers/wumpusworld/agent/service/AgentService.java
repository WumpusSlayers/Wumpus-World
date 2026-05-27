package com.wumpusslayers.wumpusworld.agent.service;

import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

public class AgentService {

    private final PathFinderService pathFinderService;

    /**
     * Gold 획득 감지 후 복귀 모드로 전환하여 (1,1)까지 복귀 경로를 계산한다.
     * hasGold == true일 때만 호출한다.
     */
    public List<Position> activateSafeReturnMode(String sessionId, Position current) {
        // Gold 획득 후 (1,1)까지 BFS 복귀 경로 계산
        List<Position> returnPath = pathFinderService.buildSafeReturnPath(sessionId, current);
        return returnPath;
    }

    /**
     * 복귀 경로의 첫 번째 이동 칸을 반환한다.
     * 경로가 비어있으면 null 반환.
     */
    public Position getNextReturnStep(List<Position> returnPath) {
        if (returnPath == null || returnPath.isEmpty()) return null;
        return returnPath.get(0);
    }

    /**
     * (1,1) 도착 시 CLIMB 액션을 반환한다.
     */
    public ActionType completeMission(Position current) {
        if (current.equals(new Position(1, 1))) {
            System.out.println("[AgentService] (1,1) 도착 → CLIMB 실행");
            return ActionType.CLIMB;
        }
        return null;
    }
}
