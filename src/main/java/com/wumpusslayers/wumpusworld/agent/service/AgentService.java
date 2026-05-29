package com.wumpusslayers.wumpusworld.agent.service;

import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wumpusslayers.wumpusworld.common.enums.Direction;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import com.wumpusslayers.wumpusworld.reasoning.service.KnowledgeUpdateService;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor

public class AgentService {

    private final PathFinderService pathFinderService;
    private final KnowledgeUpdateService knowledgeUpdateService;
    /** 세션별 이전 위치 저장 (핑퐁 방지용) */
    private final Map<String, Position> previousPositions = new ConcurrentHashMap<>();

    /**
     * Gold 획득 감지 후 복귀 모드로 전환하여 (1,1)까지 복귀 경로를 계산한다.
     * hasGold == true일 때만 호출한다.
     */
    public List<Position> activateSafeReturnMode(String sessionId, Position current) {
        // Gold 획득 후 (1,1)까지 BFS 복귀 경로 계산
        List<Position> returnPath = pathFinderService.buildSafeReturnPath(sessionId, current);
        System.out.println("[AgentService] BFS 복귀 경로: " + returnPath);
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

    /**
     * 매 턴 최적의 행동을 결정한다.
     * Gold 보유 여부를 기준으로 탐색/복귀 전략을 전환한다.
     */
    public ActionType decideNextAction(String sessionId, World world) {
        Position current = world.getAgentPosition();

        // 1. Glitter 감지 → GRAB
        if (world.getGrid().getCell(current).isHasGold()) {
            return ActionType.GRAB;
        }

        // 2. Gold 보유 → 복귀 모드
        if (world.getStatus().name().equals("WIN")) {
            // (1,1) 도착 시 CLIMB
            ActionType climb = completeMission(current);
            if (climb != null) return climb;

            // 복귀 경로 계산 후 첫 번째 이동 칸으로 이동
            List<Position> returnPath = activateSafeReturnMode(sessionId, current);
            Position nextStep = getNextReturnStep(returnPath);
            if (nextStep != null) {
                return getActionToMove(world, nextStep);
            }
        }

        // 3. 직선 방향에 Wumpus 있고 화살 있으면 SHOOT
        if (world.getArrowCount() > 0 && hasShootableWumpus(sessionId, world)) {
            return ActionType.SHOOT;
        }

        // 4. PathFinder 탐색
        Position previous = previousPositions.get(sessionId);
        Position nextCell = pathFinderService.selectNextCell(sessionId, current, previous);
        if (nextCell != null) {
            previousPositions.put(sessionId, current); // 현재 위치를 이전 위치로 저장
            return getActionToMove(world, nextCell);
        }

        return null;
    }

    /**
     * 현재 방향과 목표 위치를 기반으로 이동 액션을 결정한다.
     * 현재 방향이 목표 방향과 같으면 GO_FORWARD, 아니면 회전 액션 반환
     */
    private ActionType getActionToMove(World world, Position target) {
        Position current = world.getAgentPosition();
        Direction targetDir = getDirectionTo(current, target);
        if (targetDir == null) return null;

        Direction currentDir = world.getAgentDirection();
        if (currentDir == targetDir) return ActionType.GO_FORWARD;
        if (currentDir.turnLeft() == targetDir) return ActionType.TURN_LEFT;
        return ActionType.TURN_RIGHT;
    }

    /**
     * 현재 위치에서 목표 위치 방향을 반환한다.
     */
    private Direction getDirectionTo(Position current, Position target) {
        if (target.getX() > current.getX()) return Direction.EAST;
        if (target.getX() < current.getX()) return Direction.WEST;
        if (target.getY() > current.getY()) return Direction.NORTH;
        if (target.getY() < current.getY()) return Direction.SOUTH;
        return null;
    }

    /**
     * 현재 위치에서 직선 방향에 확정/후보 Wumpus가 있으면 true 반환
     */
    private boolean hasShootableWumpus(String sessionId, World world) {
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) return false;

        Position current = world.getAgentPosition();
        List<Position> neighbors = getNeighbors(current);

        for (Position pos : neighbors) {
            if (!isValid(pos)) continue;
            if (kb.isDefiniteWumpus(pos) || (kb.isPossibleWumpus(pos) && !kb.isDefiniteWumpus(pos))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 인접한 4방향 셀 목록을 반환한다.
     */
    private List<Position> getNeighbors(Position current) {
        List<Position> neighbors = new ArrayList<>();
        neighbors.add(new Position(current.getX() + 1, current.getY()));
        neighbors.add(new Position(current.getX() - 1, current.getY()));
        neighbors.add(new Position(current.getX(), current.getY() + 1));
        neighbors.add(new Position(current.getX(), current.getY() - 1));
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
