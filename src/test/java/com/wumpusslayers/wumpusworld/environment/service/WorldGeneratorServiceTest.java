/**
 * WorldGeneratorService가 공지사항의 규칙을 잘 준수하는지 검증하는 테스트 코드입니다.
 */
package com.wumpusslayers.wumpusworld.environment.service;

import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldGeneratorServiceTest {

    private final WorldGeneratorService worldGeneratorService = new WorldGeneratorService();

    @Test
    @DisplayName("공지사항 규칙 테스트: (1,1)은 항상 안전해야 하며 Gold는 위험 지역에 있으면 안 된다.")
    void testWorldGenerationRules() {
        // 1,000번의 테스트를 수행하여 확률적 오류나 규칙 위반이 있는지 확인
        for (int i = 0; i < 1000; i++) {
            World world = worldGeneratorService.generateWorld();

            // 1. (1,1) 안전성 체크
            Position startPos = new Position(1, 1);
            assertFalse(world.getGrid().getCell(startPos).isHasPit(), "(1,1)에 구덩이가 있으면 안 됩니다.");
            assertFalse(world.getGrid().getCell(startPos).isHasWumpus(), "(1,1)에 움푸스가 있으면 안 됩니다.");
            assertFalse(world.getGrid().getCell(startPos).isHasGold(), "(1,1)에 금이 있으면 안 됩니다.");

            // 2. Gold 배치 유효성 체크
            boolean goldFound = false;
            for (int x = 1; x <= 4; x++) {
                for (int y = 1; y <= 4; y++) {
                    Position currentPos = new Position(x, y);
                    if (world.getGrid().getCell(currentPos).isHasGold()) {
                        goldFound = true;
                        // 금이 있는 곳에 구덩이나 움푸스가 있는지 확인
                        assertFalse(world.getGrid().getCell(currentPos).isHasPit(), "금은 구덩이가 있는 곳에 배치될 수 없습니다.");
                        assertFalse(world.getGrid().getCell(currentPos).isHasWumpus(), "금은 움푸스가 있는 곳에 배치될 수 없습니다.");
                    }
                }
            }
            assertTrue(goldFound, "맵에 금이 반드시 하나는 존재해야 합니다.");
        }
        System.out.println("✅ 1,000번의 랜덤 맵 생성 테스트를 모두 통과했습니다!");
    }
}