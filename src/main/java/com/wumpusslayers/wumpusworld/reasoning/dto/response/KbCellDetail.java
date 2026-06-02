package com.wumpusslayers.wumpusworld.reasoning.dto.response;

/**
 * 격자 내 1개 셀의 실시간 지식(추론) 정보를 보관하는 DTO 레코드입니다.
 */
public record KbCellDetail(
    int x,
    int y,
    boolean visited,
    boolean safe,
    boolean possiblePit,
    boolean definitePit,
    boolean possibleWumpus,
    boolean definiteWumpus
) {}
