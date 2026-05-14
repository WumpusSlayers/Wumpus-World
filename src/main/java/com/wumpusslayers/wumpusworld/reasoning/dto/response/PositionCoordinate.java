package com.wumpusslayers.wumpusworld.reasoning.dto.response;

/**
 * 격자 상의 한 칸을 질의 응답에서 표현할 때 사용한다(1-based).
 */
public record PositionCoordinate(int x, int y) {
}
