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

    /** 격자에 {@code hasWumpus}인 칸이 하나라도 있으면 true (다중 움퍼스·SHOOT 제거 후 그리드가 진실). */
    public boolean hasAnyWumpusOnGrid() {
        for (int x = 1; x <= Grid.getSIZE(); x++) {
            for (int y = 1; y <= Grid.getSIZE(); y++) {
                if (grid.getCell(new Position(x, y)).isHasWumpus()) {
                    return true;
                }
            }
        }
        return false;
    }
    /*
     * 현재 월드의 상태를 텍스트로 시각화
     * [W]: Wumpus, [P]: Pit, [G]: Gold, [A]: Agent
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n----- Wumpus World Map -----\n");
        for (int y = 4; y >= 1; y--) { // 좌표계 특성상 위에서 아래로 출력
            for (int x = 1; x <= 4; x++) {
                Position pos = new Position(x, y);
                Cell cell = grid.getCell(pos);

                sb.append("[");
                if (agentPosition.equals(pos)) sb.append("A");
                else if (cell.isHasWumpus()) sb.append("W");
                else if (cell.isHasPit()) sb.append("P");
                else if (cell.isHasGold()) sb.append("G");
                else sb.append(" ");
                sb.append("] ");
            }
            sb.append("\n");
        }
        sb.append("Agent: ").append(agentDirection).append(" | Status: ").append(status);
        sb.append("\n----------------------------");
        return sb.toString();
    }
}
