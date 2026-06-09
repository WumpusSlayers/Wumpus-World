/**
 * PerceptService가 주변 위험 요소(Wumpus, Pit)를 정확히 감지하는지 테스트합니다.
 */
package com.wumpusslayers.wumpusworld.environment.service;

import com.wumpusslayers.wumpusworld.environment.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerceptServiceTest {

    private final PerceptService perceptService = new PerceptService();

    @Test
    @DisplayName("상하좌우에 Wumpus나 Pit이 있을 때 Stench와 Breeze를 감지해야 한다.")
    void testPerceptDetection() {
        // 1. 테스트용 World 생성
        World world = new World();
        Grid grid = world.getGrid();

        // 2. 강제로 특정 위치에 위험 요소 배치 (예: [2,1]에 Wumpus, [1,2]에 Pit)
        grid.getCell(new Position(2, 1)).setHasWumpus(true);
        grid.getCell(new Position(1, 2)).setHasPit(true);

        // 3. 에이전트는 안전한 [1,1]에 있다고 가정
        world.setAgentPosition(new Position(1, 1));

        // 4. Percept 추출 (bumped=false, screamed=false 가정)
        Percept percept = perceptService.getPercept(world, false, false);

        // 5. 검증
        assertTrue(percept.isStench(), "[2,1]에 Wumpus가 있으므로 [1,1]에서 악취가 나야 합니다.");
        assertTrue(percept.isBreeze(), "[1,2]에 Pit이 있으므로 [1,1]에서 바람이 불어야 합니다.");
        assertFalse(percept.isGlitter(), "[1,1]에는 금이 없으므로 반짝임이 없어야 합니다.");
    }

    @Test
    @DisplayName("위험 요소가 대각선에 있을 때는 감지하지 못해야 한다.")
    void testNoPerceptOnDiagonal() {
        World world = new World();
        Grid grid = world.getGrid();

        // [2,2]에 Wumpus 배치 (대각선 위치)
        grid.getCell(new Position(2, 2)).setHasWumpus(true);
        world.setAgentPosition(new Position(1, 1));

        Percept percept = perceptService.getPercept(world, false, false);

        // 대각선은 인접(Adjacent)이 아니므로 감지되면 안 됨
        assertFalse(percept.isStench(), "대각선에 있는 Wumpus의 악취는 맡을 수 없어야 합니다.");
    }
}