package com.wumpusslayers.wumpusworld.simulation.controller;

/*
 * 프론트엔드와 통신하여 게임 데이터를 주고받는 컨트롤러입니다.
 */
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.reasoning.dto.response.KnowledgeSummaryResponse;
import com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService;
import com.wumpusslayers.wumpusworld.simulation.service.GameEngine;
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 프론트엔드 포트가 달라도 통신 가능하게 허용
public class GameController {

    private final GameEngine gameEngine;
    private final ReasoningService reasoningService;

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

    /*
     * 세션별 KB 요약(안전·방문 칸, 움퍼스 플래그) — 플래닝·Postman 디버그용(#14)
     */
    @GetMapping("/reasoning/summary")
    public KnowledgeSummaryResponse getReasoningSummary(@RequestParam String userId) {
        return reasoningService.getKnowledgeSummary(userId);
    }

    /*
     * 에이전트 자동 행동 API
     * POST /api/game/auto?userId=..
     */
    @PostMapping("/auto")
    public Action autoAction(@RequestParam String userId) {
        return gameEngine.processAutoAction(userId);
    }

    /*
     * 게임 종료까지 AgentService가 자동으로 실행하는 API
     * POST /api/game/run?userId=..
     */
    @PostMapping("/run")
    public List<Action> runGame(@RequestParam String userId) {
        return gameEngine.runAutoGame(userId);
    }
}
