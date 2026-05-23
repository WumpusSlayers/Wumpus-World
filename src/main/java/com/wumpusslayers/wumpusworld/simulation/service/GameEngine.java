package com.wumpusslayers.wumpusworld.simulation.service;

/*
 * 게임의 생성부터 종료까지 전체 생명주기를 관리하는 엔진
 */
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.agent.service.ActionPlannerService;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.environment.service.PerceptService;
import com.wumpusslayers.wumpusworld.environment.service.WorldGeneratorService;
import com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임 월드와 추론 KB는 동일한 {@code userId} 키로 묶인다(#15).
 * {@link #startNewGame(String)} 에서만 KB를 비우고, 사망 후 {@code (1,1)} 복귀 시에는 KB를 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class GameEngine {

    private final WorldGeneratorService worldGeneratorService;
    private final ActionPlannerService actionPlannerService;
    private final PerceptService perceptService;
    private final ReasoningService reasoningService;

    // 여러 사용자가 접속할 수 있으므로 메모리에 게임 상태 저장 (Key: 세션ID 또는 유저명)
    private final ConcurrentHashMap<String, World> gameSessions = new ConcurrentHashMap<>();

    /**
     * 새 World를 만들고 세션을 등록한 뒤, KB를 초기화하고 시작 칸 percept로 추론까지 반영한다(#14·#15).
     * 완전히 새 게임일 때만 추론 세션을 비운다. 사망 리스폰은 {@link #processAction(String, ActionType)} 경로에서 처리한다.
     */
    public World startNewGame(String userId) {
        requireUserId(userId);
        reasoningService.resetKnowledgeForNewGame(userId);
        World world = worldGeneratorService.generateWorld();
        gameSessions.put(userId, world);

        var initialPercept = perceptService.getPercept(world, false, false);
        reasoningService.updateFromObservation(userId, world.getAgentPosition(), initialPercept);
        reasoningService.syncWumpusAlive(userId, world.hasAnyWumpusOnGrid());

        System.out.println("새로운 게임 시작! 유저: " + userId);
        System.out.println(world.toString());
        return world;
    }

    /**
     * 액션 실행 후 현재 칸 percept로 KB를 갱신하고 추론을 돌린다(#14·#15).
     * {@link ActionPlannerService} 가 구덩이·움퍼스로 에이전트를 (1,1)로 되돌려도 KB는 유지된다(#15).
     */
    public Action processAction(String userId, ActionType actionType) {
        requireUserId(userId);
        World world = gameSessions.get(userId);
        if (world == null) {
            throw new SimulationException("진행 중인 게임이 없습니다.");
        }

        // executeAction에 userId 추가
        Action result = actionPlannerService.executeAction(world, actionType, userId);
        if (result.isDiedInPit() && result.getActionPosition() != null) {
            reasoningService.markAgentDiedInPit(userId, result.getActionPosition());
        }
        if (result.isDiedInWumpus() && result.getActionPosition() != null) {
            reasoningService.markAgentDiedInWumpus(userId, result.getActionPosition());
        }
        reasoningService.syncWumpusAlive(userId, world.hasAnyWumpusOnGrid());
        reasoningService.updateFromObservation(userId, result.getActionPosition(), result.getPercept());

        System.out.println("액션 실행: " + actionType + " | 결과: " + result.getMessage());
        System.out.println(world.toString());

        return result;
    }

    /** 세션에 연결된 World가 없으면 null을 반환한다. */
    public World getGameStatus(String userId) {
        requireUserId(userId);
        return gameSessions.get(userId);
    }

    /** userId가 null·공백이면 {@link SimulationException}을 던진다. */
    private static void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new SimulationException("userId must not be null or blank");
        }
    }
}
