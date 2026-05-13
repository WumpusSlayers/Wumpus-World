package com.wumpusslayers.wumpusworld.agent.domain;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Action {
    private final Percept percept;     // 행동 후 에이전트가 느끼는 감각
    private final boolean isGameOver;  // 게임 종료 여부
    private final String message;      // 상황 설명 메시지
}

