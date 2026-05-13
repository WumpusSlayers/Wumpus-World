package com.wumpusslayers.wumpusworld.agent.service;

import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.common.enums.Direction;
import com.wumpusslayers.wumpusworld.common.enums.GameStatus;
import com.wumpusslayers.wumpusworld.environment.domain.Cell;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.environment.domain.World;
import com.wumpusslayers.wumpusworld.environment.service.PerceptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActionPlannerService {

    private final PerceptService perceptService;

    public Action executeAction(World world, ActionType actionType){
        boolean bumped = false;
        boolean screamed = false;
        String message = "";

        switch (actionType) {
            case GO_FORWARD -> {
                bumped = moveForward(world);
                message = bumped ? "벽에 부딪혔습니다! (Bump)" : "앞으로 한 칸 이동 했습니다.";
                checkSurvival(world); //이동 후 생존 확인
            }

            case TURN_LEFT -> {
                world.setAgentDirection(world.getAgentDirection().turnLeft());
                message = "왼쪽으로 회전했습니다.";
            }
            case TURN_RIGHT -> {
                world.setAgentDirection(world.getAgentDirection().turnRight());
                message = "오른쪽으로 회전했습니다.";
            }
            case GRAB -> {
                if (world.getGrid().getCell(world.getAgentPosition()).isHasGold()) {
                    world.getGrid().getCell(world.getAgentPosition()).setHasGold(false);
                    world.setStatus(GameStatus.WIN);
                    message = "금을 획득했습니다! 이제 (1,1)로 돌아가세요.";
                } else {
                    message = "이곳에는 금이 없습니다.";
                }
            }
            case CLIMB -> {
                if (world.getAgentPosition().equals(new Position(1, 1)) && world.getStatus() == GameStatus.WIN) {
                    world.setStatus(GameStatus.ESCAPED);
                    message = "동굴을 무사히 탈출했습니다! 승리!";
                } else {
                    message = "탈출할 수 없는 상태입니다.";
                }
            }
            case SHOOT -> {
                //TODO: 언제 화살을 쏠지 로직 구현
            }
        }

        // 행동 후 새로운 지각 정보 생성
        Percept percept = perceptService.getPercept(world, bumped, screamed);
        boolean isGameOver = world.getStatus() != GameStatus.PLAYING && world.getStatus() != GameStatus.WIN;

        return Action.builder()
                .percept(percept)
                .isGameOver(isGameOver || world.getStatus() == GameStatus.ESCAPED)
                .message(message)
                .build();
    }


    private boolean moveForward(World world) {
        Position current = world.getAgentPosition();
        Direction dir = world.getAgentDirection();

        int nextX = current.getX() + (dir == Direction.EAST ? 1 : dir ==Direction.WEST ? -1 : 0);
        int nextY = current.getY() + (dir == Direction.NORTH ? 1 : dir == Direction.SOUTH ? -1 : 0);

        Position nextPos = new Position(nextX, nextY);

        //벽에 부딪히지 않았을 때
        if (world.getGrid().isValid(nextPos)) {
            world.setAgentPosition(nextPos);
            world.getGrid().getCell(nextPos).setVisited(true);
            return false;
        }
        return true; //벽에 부딪힘
    }

    private void checkSurvival(World world) {
        Cell currentCell = world.getGrid().getCell(world.getAgentPosition());
        if (currentCell.isHasPit()) {
            world.setStatus(GameStatus.LOSE_PIT);
        } else if (currentCell.isHasWumpus()) {
            world.setStatus(GameStatus.LOSE_WUMPUS);
        }
    }
}
