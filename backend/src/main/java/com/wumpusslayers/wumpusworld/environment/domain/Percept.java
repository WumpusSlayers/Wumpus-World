package com.wumpusslayers.wumpusworld.environment.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Percept {
    boolean stench;   // 악취 (Wumpus 인접)
    boolean breeze;   // 바람 (Pit 인접)
    boolean glitter;  // 반짝임 (Gold 같은 칸)
    boolean bump;     // 부딪힘 (벽 충돌)
    boolean scream;   // 비명 (Wumpus 사망)
}
