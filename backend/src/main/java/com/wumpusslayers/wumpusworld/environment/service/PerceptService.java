package com.wumpusslayers.wumpusworld.environment.service;

/*
* 현재 World 상태를 분석하여 에이전트에게 전달할 Percept 계산*/
import com.wumpusslayers.wumpusworld.environment.domain.*;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;

@Service
public class PerceptService {

    /* 에이전트의 현재 위치와 주변환경을 체크하여 지각 정보 획득*/
    public Percept getPercept(World world, boolean bumped, boolean screamed) {

        //현재 위치 확인
        Position pos = world.getAgentPosition();
        Grid grid = world.getGrid();

        //Percept 생성
        //Adjacent(인접한)
        return Percept.builder()
                //현재 위치 주변에 Wumpus가 있는지
                .stench(isAdjacentToWumpus(grid, pos))
                //현재 위치 주변에 웅덩이가 있는지
                .breeze(isAdjacentToPit(grid, pos))
                //현재 칸에 금 있는지
                .glitter(grid.getCell(pos).isHasGold())
                .bump(bumped)
                .scream(screamed)
                .build();
    }

    //현재 위치 주변에 Wumpus가 있는지
    private boolean isAdjacentToWumpus(Grid grid, Position pos) {
        return hasAdjacentCondition(grid, pos, Cell::isHasWumpus);
    }

    //현재 위치 주변에 웅덩이가 있는지
    private boolean isAdjacentToPit(Grid grid, Position pos) {
        return hasAdjacentCondition(grid, pos, Cell::isHasPit);
    }

    /* 상하좌우 인접한 칸들 검사*/
    private boolean hasAdjacentCondition(Grid grid, Position pos, Predicate<Cell> condition ) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        for (int i = 0; i < 4; i++) {
            Position adjPos = new Position(pos.getX() + dx[i], pos.getY() + dy[i]);
            //벽 밖 제외
            if (grid.isValid(adjPos)) {
                //조건 검사
                if (condition.test(grid.getCell(adjPos))) {
                    return true;
                }
            }
        }
        return false;
    }

}
