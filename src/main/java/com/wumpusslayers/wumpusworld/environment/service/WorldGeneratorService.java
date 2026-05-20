package com.wumpusslayers.wumpusworld.environment.service;

import com.wumpusslayers.wumpusworld.environment.domain.Cell;
import com.wumpusslayers.wumpusworld.environment.domain.Grid;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 확률 규칙(0.1)에 따라 Wumpus World의 구성 요소를 배치하는 서비스입니다.
 * 랜덤하게 환경을 생성하고 에이전트의 시작 지점 안전을 보장합니다.
 */

@Service
public class WorldGeneratorService {

    private final Random random = new Random();

    // 새로운 게임 환경(World)를 생성하고 구성 요소를 배치
    public World generateWorld() {
        World world = new World();
        Grid grid = world.getGrid();

        //Pit 및 Wumpus 배치 (확률 0.1)
        placeHazards(grid);

        //(1,1) 안전 보장
        ensureStartZoneSafety(grid);

        //Gold 배치
        placeGold(grid);

        world.setWumpusAlive(world.hasAnyWumpusOnGrid());
        return world;
    }

    //각 격자마다 10% 확률로 Pit 와 Wumpus를 배치
    private void placeHazards(Grid grid) {
        for (int x = 1; x <= Grid.getSIZE(); x++) {
            for (int y = 1; y <= Grid.getSIZE(); y++) {
                Position pos = new Position(x, y);
                Cell cell = grid.getCell(pos);

                //웅덩이 배치
                if (random.nextDouble() < 0.10) {
                    cell.setHasPit(true);
                }
                //Wumpus 배치 (구덩이가 없는 곳에만 배치하여 겹침 방지)
                else if (random.nextDouble() < 0.10) {
                    cell.setHasWumpus(true);
                }
            }
        }
    }

    // 시작지점 및 인접 칸 안전 보장
    private void ensureStartZoneSafety(Grid grid) {
        // (1,1) 시작점 안전
        Cell startCell = grid.getCell(new Position(1, 1));
        startCell.setHasPit(false);
        startCell.setHasWumpus(false);
        startCell.setHasGold(false);

        // (1,2) 안전
        Cell adjacent1 = grid.getCell(new Position(1, 2));
        adjacent1.setHasPit(false);
        adjacent1.setHasWumpus(false);

        // (2,1) 안전
        Cell adjacent2 = grid.getCell(new Position(2, 1));
        adjacent2.setHasPit(false);
        adjacent2.setHasWumpus(false);
    }

    // Pit이나 Wumpus가 없는 안전한 칸 중 하나를 골라 금을 배치
    private void placeGold(Grid grid) {
        List<Position> safePositions = new ArrayList<>();

        for (int x = 1; x <= Grid.getSIZE(); x++) {
            for (int y = 1; y <= Grid.getSIZE(); y++) {
                Position pos = new Position(x, y);
                Cell cell = grid.getCell(pos);

                // 위험 요소가 없는 칸 후보군
                if (!cell.isHasPit() && !cell.isHasWumpus() && !(x==1 && y==1)) {
                    safePositions.add(pos);
                }

            }
        }

        if (!safePositions.isEmpty()) {
            Position goalPos = safePositions.get(random.nextInt(safePositions.size()));
            grid.getCell(goalPos).setHasGold(true);
        }
    }
}
