package com.wumpusslayers.wumpusworld.environment.domain;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import lombok.Getter;

/*
* 4x4 크기의 격자 전체를 생성하고 관리하는 코드*/
@Getter
public class Grid {
    /* Wumpus World
        (1,4) (2,4) (3,4) (4,4)
        (1,3) (2,3) (3,3) (4,3)
        (1,2) (2,2) (3,2) (4,2)
        (1,1) (2,1) (3,1) (4,1)
     */
    @Getter //외부에서 Grid.getSIZE()로 접근
    private static final int SIZE = 4;

    private final Cell[][] cells;

    public Grid() {
        //격자배열 초기화
        this.cells = new Cell[SIZE][SIZE];
        for (int i = 0; i <SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                cells[i][j] = new Cell();
            }
        }
    }

    // 특정 칸의 정보를 반환
    public Cell getCell(Position pos) {
        if (!isValid(pos)) {
            throw new SimulationException("유효하지 않은 좌표 입니다: (" + pos.getX() + ", " + pos.getY() + ")");
        }
        return cells[pos.getX() - 1][pos.getY() - 1]; //1씩 뺴줘야 배열로 변환 가능
    }

    //에이전트가 벽에 부딪혔는지 확인
    public boolean isValid(Position pos) {
        return pos.getX() >= 1 && pos.getX() <= SIZE  &&
                pos.getY() >= 1 && pos.getY() <= SIZE;
    }
}
