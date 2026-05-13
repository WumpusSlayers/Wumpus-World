package com.wumpusslayers.wumpusworld.simulation.controller;

/*
 * 프론트엔드와 통신하여 게임 데이터를 주고받는 컨트롤러입니다.
 */
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.simulation.service.GameEngine;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 프론트엔드 포트가 달라도 통신 가능하게 허용
public class GameController {

    private final GameEngine gameEngine;

    /*
     * 새로운 게임 생성 API
     * POST /api/game/start?userId=..
     */
    @PostMapping("/start")
    public World createGame(@RequestParam String userId) {
        return gameEngine.startNewGame(userId);
    }

    /*
     * 에이전트 액션 수행 API
     * POST /api/game/action?userId=...&type=GO_FORWARD
     */
    @PostMapping("/action")
    public Action execute(@RequestParam String userId, @RequestParam ActionType type) {
        return gameEngine.processAction(userId, type);
    }

    /*
     * 현재 맵 상태 조회 API (디버깅/프론트 렌더링용)
     */
    @GetMapping("/status")
    public World getStatus(@RequestParam String userId) {
        return gameEngine.getGameStatus(userId);
    }
}
