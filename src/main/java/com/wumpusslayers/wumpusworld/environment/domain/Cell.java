package com.wumpusslayers.wumpusworld.environment.domain;

/*
 * 격자의 개별 칸이 가지고 있는 상태 정보를 저장
 * */

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Cell {
    private boolean hasWumpus = false;
    private boolean hasPit = false;
    private boolean hasGold = false;
    private boolean isVisited = false;
}
