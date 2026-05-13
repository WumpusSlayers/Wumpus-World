package com.wumpusslayers.wumpusworld.common.enums;
/*
* 게임의 진행 상태(진행중, 승리, 사망원인 등)를 관리
* 에이전트가 웅덩이에 빠졌는지, 괴물에게 잡혔는지 등의 결과를 구분
* */
public enum GameStatus {
    //현재 에이전트가 동굴을 탐험중인 상태
    PLAYING,
    // 에이전트가 금을 찾은 상태 (게임이 끝나진 않았으며 (1,1)로 돌아가야함)
    WIN,
    // 웅덩이에 빠져 게임 실패
    LOSE_PIT,
    // 에이전트가 살아있는 wumpus의 칸에 진입하여 잡아먹힌 상태
    LOSE_WUMPUS,
    // 금을 획득한 에이전트가 시작지점인 [1,1]로 돌아와 climb 수행
    ESCAPED
}
