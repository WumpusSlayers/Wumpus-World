package com.wumpusslayers.wumpusworld.agent.service;

import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import com.wumpusslayers.wumpusworld.reasoning.service.KnowledgeUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

@Service
@RequiredArgsConstructor

public class PathFinderService {

    private final KnowledgeUpdateService knowledgeUpdateService;

    /**
     * 우선순위 기반으로 다음 이동할 셀을 선택한다.
     * 1순위: Safe && 미방문 (인접) → 안전하게 새로운 칸 탐색
     * 2순위: BFS → Safe 칸만 경유하여 가장 가까운 Safe 미방문 칸 탐색 (핑퐁 방지)
     * 3순위: 완전 청정 Unknown (인접) → Pit/Wumpus 후보조차 아닌 순수 미개척지 공격적 진입
     * 4순위: Pit 후보 (인접) → 안전지대 소진 시 Pit 후보 칸 모험 진입
     * 최후의 보루: 완전 고립 시 Safe 방문 칸으로 후퇴
     * Pit 확정 / Wumpus 확정 셀은 탐색 대상에서 영구 제외
     */
    public Position selectNextCell(String sessionId, Position current, Position previous) {
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) return null;

        List<Position> neighbors = getNeighbors(current);
        Position backtrackCandidate = null;

        // 1순위: 인접 Safe 미방문
        for (Position pos : neighbors) {
            if (isValid(pos) && kb.isSafe(pos) && !kb.isVisited(pos)) {
                System.out.println("[PathFinder] 1순위: 안전한 미방문 칸 이동 -> " + pos);
                return pos;
            }
        }

        // 2순위: BFS로 가장 가까운 Safe 미방문 칸 탐색 (핑퐁 원천 차단)
        Position bfsNext = bfsFindNearestUnvisitedSafe(kb, current);
        if (bfsNext != null) {
            System.out.println("[PathFinder] 2순위 BFS: 가장 가까운 Safe 미방문 칸 탐색 → 다음 이동 칸 " + bfsNext);
            return bfsNext;
        }

        // 3순위: Unknown 공격적 진입 (확정 위험이 없는 순수 미개척지)
        for (Position pos : neighbors) {
            if (isValid(pos) && !kb.isVisited(pos)
                    && !kb.isDefinitePit(pos) && !kb.isDefiniteWumpus(pos) // 확정 위험지역 절대 금지
                    && !kb.isPossiblePit(pos)) { // 그 와중에 Pit 후보조차 아닌 완전 청정 Unknown
                System.out.println("[PathFinder] 3순위: Unknown 공격적 진입 -> " + pos);
                return pos;
            }
        }

        // 4순위: Pit 후보 진입 (안전지대가 아예 없고, 3순위 청정 Unknown도 없을 때만)
        for (Position pos : neighbors) {
            if (isValid(pos) && !kb.isVisited(pos)
                    && !kb.isDefinitePit(pos) && !kb.isDefiniteWumpus(pos)) {
                System.out.println("[PathFinder] 4순위: Pit 후보 모험 진입 -> " + pos);
                return pos;
            }
        }

        /*
        // 5순위: 고립 시 BFS로 안전한 칸만 밟고 다른 안전구역으로 탈출 (핑퐁 차단)
        Position bfsEscapeNext = bfsFindNearestUnvisitedSafe(kb, current);
        if (bfsEscapeNext != null) {
            System.out.println("[PathFinder] 5순위: 고립 상태 발생! BFS로 안전 구역 탈출 -> " + bfsEscapeNext);
            return bfsEscapeNext;
        }
        */

         // 최후의 보루: 안전한 칸으로 후퇴하되, 바로 직전 칸(previous)으로 되돌아가며 핑퐁 치는 것을 원천 차단!
        Position backup = null;
        for (Position pos : neighbors) {
            if (isValid(pos) && kb.isSafe(pos)) {
                // 직전 칸(previous)이 아닌 '다른 안전한 방문 칸'이 주변에 있다면 거기로 먼저 후퇴
                if (previous == null || !pos.equals(previous)) {
                    System.out.println("[PathFinder] 고립 발생! 다른 안전한 칸으로 후퇴 -> " + pos);
                    return pos;
                }
                backup = pos; // 사방이 다 막혀서 진짜 어쩔 수 없이 왔던 길로 가야 할 때만 백업으로 저장
            }
        }

        if (backup != null) {
            System.out.println("[PathFinder] 완전히 막힘! 어쩔 수 없이 직전 칸으로 후퇴 -> " + backup);
            return backup;
        }

        return null;
    }

    /**
     * Gold 획득 후 현재 위치에서 (1,1)까지 BFS 기반 최단 경로를 계산한다.
     * Safe 방문 칸을 경유하며, Pit/Wumpus 확정 칸은 경로에서 제외한다.
     * 경로가 없으면 빈 리스트를 반환한다.
     */
    public List<Position> buildSafeReturnPath(String sessionId, Position current) {
        KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
        if (kb == null) return new ArrayList<>();

        Position goal = new Position(1, 1);

        // 이미 (1,1)에 있으면 빈 리스트 반환
        if (current.equals(goal)) return new ArrayList<>();

        // BFS 탐색
        Queue<Position> queue = new LinkedList<>();
        Map<Position, Position> parentMap = new HashMap<>();

        queue.add(current);
        parentMap.put(current, null);

        while (!queue.isEmpty()) {
            Position now = queue.poll();

            for (Position next : getNeighbors(now)) {
                if (!isValid(next) || parentMap.containsKey(next)) continue;
                // Pit/Wumpus 확정 칸은 경로 제외
                if (kb.isDefinitePit(next) || kb.isDefiniteWumpus(next)) continue;
                // Safe 칸만 경유 가능
                if (!kb.isSafe(next)) continue;

                parentMap.put(next, now);

                // 목표 도달
                if (next.equals(goal)) {
                    return reconstructPath(parentMap, current, goal);
                }

                queue.add(next);
            }
        }

        return new ArrayList<>();
    }

    /**
     * parentMap을 역추적하여 start에서 goal까지의 경로를 반환한다.
     */
    private List<Position> reconstructPath(Map<Position, Position> parentMap, Position start, Position goal) {
        List<Position> path = new ArrayList<>();
        Position current = goal;

        while (!current.equals(start)) {
            path.add(0, current);
            current = parentMap.get(current);
        }

        return path;
    }

    /**
     * BFS로 현재 위치에서 가장 가까운 Safe 미방문 칸까지의
     * 첫 번째 이동 칸을 반환한다.
     * Safe 방문 칸만 경유 가능, Pit/Wumpus 확정 칸 제외
     */
    private Position bfsFindNearestUnvisitedSafe(KnowledgeBase kb, Position start) {
        Queue<Position> queue = new LinkedList<>();
        Map<Position, Position> parentMap = new HashMap<>();
        queue.add(start);
        parentMap.put(start, null);

        while (!queue.isEmpty()) {
            Position current = queue.poll();

            for (Position next : getNeighbors(current)) {
                if (!isValid(next) || parentMap.containsKey(next)) continue;
                if (kb.isDefinitePit(next) || kb.isDefiniteWumpus(next)) continue;
                //if (!kb.isSafe(next)) continue; // Safe 칸만 경유

                parentMap.put(next, current);

                // 목표 체크 먼저 (Safe 아닌 Unknown도 목표가 될 수 있음)
                if (!kb.isVisited(next) && !kb.isPossiblePit(next) && !kb.isPossibleWumpus(next)) {
                    return getFirstStep(parentMap, start, next);
                }

                // 경유는 Safe 칸만 큐에 추가
                if (kb.isSafe(next)) {
                    queue.add(next);
                }

                //queue.add(next);
            }
        }
        return null;
    }

    /**
     * parentMap을 역추적하여 start에서 target까지의 첫 번째 이동 칸을 반환한다.
     */
    private Position getFirstStep(Map<Position, Position> parentMap, Position start, Position target) {
        Position current = target;
        while (!parentMap.get(current).equals(start)) {
            current = parentMap.get(current);
        }
        return current;
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
