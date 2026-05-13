package com.wumpusslayers.wumpusworld.simulation.service;

/*
 * 게임의 생성부터 종료까지 전체 생명주기를 관리하는 엔진
 */
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.agent.service.ActionPlannerService;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.environment.service.WorldGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameEngine {

    private final WorldGeneratorService worldGeneratorService;
    private final ActionPlannerService actionPlannerService;

    // 여러 사용자가 접속할 수 있으므로 메모리에 게임 상태 저장 (Key: 세션ID 또는 유저명)
    private final ConcurrentHashMap<String, World> gameSessions = new ConcurrentHashMap<>();

    //새로운 게임 시작 -> world 생성
    public World startNewGame(String userId) {
        World world = worldGeneratorService.generateWorld();
        gameSessions.put(userId, world);

        System.out.println("새로운 게임 시작! 유저: " + userId);
        System.out.println(world.toString());
        return world;
    }

    //특정 유저의 action을 처리
    public Action processAction(String userId, ActionType actionType) {
        World world = gameSessions.get(userId);
        if (world == null) {
            throw new RuntimeException("진행 중인 게임이 없습니다.");
        }

        Action result = actionPlannerService.executeAction(world, actionType);

        System.out.println("액션 실행: " + actionType + " | 결과: " + result.getMessage());
        System.out.println(world.toString());

        return result;
    }

    public World getGameStatus(String userId) {
        return gameSessions.get(userId);
    }
}
