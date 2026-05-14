package com.wumpusslayers.wumpusworld.reasoning.dto.response;

import java.util.List;

/**
 * 플래닝·디버깅용 KB 요약(안전/방문 칸 좌표와 전역 플래그).
 */
public record KnowledgeSummaryResponse(
        List<PositionCoordinate> safeCells,
        List<PositionCoordinate> visitedCells,
        boolean wumpusAlive,
        boolean heardScream
) {
    /** 세션에 KB가 없을 때 사용한다. */
    public static KnowledgeSummaryResponse empty() {
        return new KnowledgeSummaryResponse(List.of(), List.of(), true, false);
    }
}
