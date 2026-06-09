package com.wumpusslayers.wumpusworld.agent.domain;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Action {
    private final Percept percept;            // 행동 후 에이전트가 느끼는 감각
    private final boolean isGameOver;         // 게임 종료 여부
    private final String message;             // 상황 설명 메시지
    private final Position actionPosition;    // 방금 행동/관측이 일어난 위치(#35)
    /**
     * 이번 GO_FORWARD에서 Pit으로 사망했는지. KB definitePit 동기화용(#34).
     */
    @Builder.Default
    private final boolean diedInPit = false;
    /**
     * 이번 GO_FORWARD에서 Wumpus에 사망했는지. KB definiteWumpus 동기화용(#37).
     */
    @Builder.Default
    private final boolean diedInWumpus = false;
}
