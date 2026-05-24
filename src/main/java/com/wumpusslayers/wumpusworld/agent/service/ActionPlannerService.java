package com.wumpusslayers.wumpusworld.agent.service;

// Wumpus 후보군 리스트 관리
import java.util.ArrayList;
import java.util.List;
import com.wumpusslayers.wumpusworld.agent.domain.Action;
import com.wumpusslayers.wumpusworld.common.enums.ActionType;
import com.wumpusslayers.wumpusworld.common.enums.Direction;
import com.wumpusslayers.wumpusworld.common.enums.GameStatus;
import com.wumpusslayers.wumpusworld.environment.domain.*;
import com.wumpusslayers.wumpusworld.environment.service.PerceptService;
// Wumpus 후보군 조회를 위한 KB 도메인
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
// 세션별 KB 조회 서비스
import com.wumpusslayers.wumpusworld.reasoning.service.KnowledgeUpdateService;
import com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActionPlannerService {

    private final PerceptService perceptService;
    /** KB 조회를 위한 세션별 지식 관리 서비스 */
    // KnowledgeUpdateService 필드 추가
    private final KnowledgeUpdateService knowledgeUpdateService;
    private final ReasoningService reasoningService;
    // executeAction 파라미터에 sessionId 추가
    public Action executeAction(World world, ActionType actionType, String sessionId){
        boolean bumped = false;
        boolean screamed = false;
        String message = "";
        DeathCause death = DeathCause.NONE;

        // 1. 방금 행동/관측이 일어난 위치 (리셋 되기 전 원래 위치)
        Position actionPos = world.getAgentPosition();


        switch (actionType) {
            case GO_FORWARD -> {
                bumped = moveForward(world);
                message = bumped ? "벽에 부딪혔습니다! (Bump)" : "앞으로 한 칸 이동 했습니다.";
                actionPos = world.getAgentPosition(); // 이동 후의 실제 위치로 갱신
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
                if (world.getArrowCount() <= 0) {
                    message = "화살이 없습니다.";
                    break;
                }
                KnowledgeBase kb = knowledgeUpdateService.getKnowledgeBaseOrNull(sessionId);
                List<Position> wumpusCandidates = getWumpusCandidates(kb);

                //움프스 후보군 수 확인
                System.out.println("Wumpus 후보군: " + wumpusCandidates);
                System.out.println("후보군 수: " + wumpusCandidates.size());

                if (wumpusCandidates.size() == 2) {
                        // 후보 2개 중 랜덤으로 타겟 선택
                        Position target = wumpusCandidates.get((int)(Math.random() * 2));
                        // 타겟 방향으로 에이전트 회전
                        Direction shootDir = getDirectionToTarget(world.getAgentPosition(), target);

                        // 타겟 방향으로 실제 회전 적용
                        if (shootDir != null) {
                            world.setAgentDirection(shootDir);
                        }

                        screamed = shootArrow(world, sessionId, kb);
                        System.out.println("선택된 타겟: " + target);
                        System.out.println("발사 방향: " + shootDir);
                        System.out.println("남은 화살 수: " + world.getArrowCount());
                        message = screamed
                                ? "화살이 Wumpus에 명중했습니다!"
                                : "화살이 빗나갔습니다.";
                } else {
                    message = "Wumpus 후보군이 2개가 아닙니다.";
                }
            }
        }

        // 2. 리셋되기 전에 현재(실제) 위치를 기준으로 Percept를 구하기
        Percept percept = perceptService.getPercept(world, bumped, screamed);

        // 3. 행동 후 생존 여부 확인 (구덩이나 움퍼스면 여기서 1,1로 리셋됨)
        if (actionType == ActionType.GO_FORWARD) {
            death = checkSurvival(world);
        }

        boolean isGameOver = world.getStatus() != GameStatus.PLAYING && world.getStatus() != GameStatus.WIN;

        return Action.builder()
                .percept(percept)
                .isGameOver(isGameOver || world.getStatus() == GameStatus.ESCAPED)
                .message(message)
                .actionPosition(actionPos)
                .diedInPit(death == DeathCause.PIT)
                .diedInWumpus(death == DeathCause.WUMPUS)
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

    /**
     * 생존 여부 판단. 사망 원인을 반환하고 (1,1)로 리셋한다(#34·#37).
     */
    private DeathCause checkSurvival(World world) {
        Cell currentCell = world.getGrid().getCell(world.getAgentPosition());

        // 구덩이에 빠진 경우
        if (currentCell.isHasPit()) {
            System.out.println("⚠️ [EVENT] 구덩이에 빠졌습니다! (1,1)에서 다시 시작합니다.");
            resetAgent(world, GameStatus.PLAYING);
            return DeathCause.PIT;
        }
        // 웜파스에게 잡힌 경우
        else if (currentCell.isHasWumpus()) {
            System.out.println("⚠️ [EVENT] 웜파스에게 잡아먹혔습니다! (1,1)에서 다시 시작합니다.");
            resetAgent(world, GameStatus.PLAYING);
            return DeathCause.WUMPUS;
        }
        return DeathCause.NONE;
    }

    /** 사망 원인(없음/Pit/Wumpus). #34·#37 KB 동기화 트리거 식별용. */
    private enum DeathCause { NONE, PIT, WUMPUS }

    /*
     * 에이전트의 위치와 상태를 초기화
     */
    private void resetAgent(World world, GameStatus nextStatus) {
        world.setAgentPosition(new Position(1, 1)); // 시작 위치로 이동
        world.setAgentDirection(Direction.EAST);    // 기본 방향(동쪽) 설정
        world.setStatus(nextStatus);                // 게임 상태 유지/변경

        // (1,1)은 항상 방문한 것으로 처리
        world.getGrid().getCell(new Position(1, 1)).setVisited(true);
    }

    /**
     * KB에서 Wumpus 후보 위치 목록을 반환한다.
     * KB가 null이면 빈 리스트를 반환한다.
     */
    private List<Position> getWumpusCandidates(KnowledgeBase kb) {
        List<Position> candidates = new ArrayList<>();
        if (kb == null) return candidates;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                // 확정 칸 제외, 후보 칸만 포함
                if (kb.isPossibleWumpus(p) && !kb.isDefiniteWumpus(p)) {
                    candidates.add(p);
                }
            }
        }
        return candidates;
    }

    /**
     * 현재 에이전트 방향으로 화살을 발사한다.
     * Wumpus에 명중하면 해당 칸에서 제거하고 true(scream)를 반환한다.
     */
    private boolean shootArrow(World world, String sessionId, KnowledgeBase kb) {
        world.useArrow();
        Direction dir = world.getAgentDirection();
        Position current = world.getAgentPosition();
        int dx = dir == Direction.EAST ? 1 : dir == Direction.WEST ? -1 : 0;
        int dy = dir == Direction.NORTH ? 1 : dir == Direction.SOUTH ? -1 : 0;
        int x = current.getX() + dx;
        int y = current.getY() + dy;

        while (x >= 1 && x <= Grid.getSIZE() && y >= 1 && y <= Grid.getSIZE()) {
            Position pos = new Position(x, y);
            Cell cell = world.getGrid().getCell(pos);
            if (cell.isHasWumpus()) {
                cell.setHasWumpus(false);
                world.setWumpusAlive(world.hasAnyWumpusOnGrid());
                if (kb != null) {
                    kb.setPossibleWumpus(pos, false);  // 명중 칸만 제거
                }
                return true;
            }
            x += dx;
            y += dy;
        }

        // 빗나갔을 때 — kb를 파라미터로 받았으니 null 체크만
        if (kb != null) {
            List<Position> missedPath = new ArrayList<>();
            x = current.getX() + dx;
            y = current.getY() + dy;
            while (x >= 1 && x <= Grid.getSIZE() && y >= 1 && y <= Grid.getSIZE()) {
                missedPath.add(new Position(x, y));
                x += dx;
                y += dy;
            }
            reasoningService.markArrowMissPath(sessionId, missedPath, kb);
        }
        return false;
    }

    /**
     * 현재 위치에서 타겟 위치 방향을 반환한다.
     * 같은 행/열이 아니면 null 반환 (직선 방향만 지원, 대각선에 위치한 경우는 X)
     */
    private Direction getDirectionToTarget(Position current, Position target) {
        if (target.getX() > current.getX()) return Direction.EAST;
        if (target.getX() < current.getX()) return Direction.WEST;
        if (target.getY() > current.getY()) return Direction.NORTH;
        if (target.getY() < current.getY()) return Direction.SOUTH;
        return null;
    }
}
