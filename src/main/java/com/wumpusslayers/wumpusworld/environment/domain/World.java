package com.wumpusslayers.wumpusworld.environment.domain;
/*
* Wumpus World의 전체 환경 상태를 통합 관리
* */

import com.wumpusslayers.wumpusworld.common.enums.Direction;
import com.wumpusslayers.wumpusworld.common.enums.GameStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
public class World {
    private final Grid grid;

    @Setter
    private Position agentPosition;

    @Setter
    private Direction agentDirection;

    private int arrowCount;

    @Setter
    private boolean isWumpusAlive;

    @Setter
    private GameStatus status;

    public World() {
        this.grid = new Grid();
        this.agentPosition = new Position(1, 1);
        this. agentDirection = Direction.EAST;
        this.arrowCount = 3;
        this.isWumpusAlive = true;
        this.status = GameStatus.PLAYING;

        this.grid.getCell(this.agentPosition).setVisited(true);
    }

    public void useArrow() {
        if (this.arrowCount > 0) {
            this.arrowCount--;
        }
    }
}
