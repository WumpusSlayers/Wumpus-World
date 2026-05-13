package com.wumpusslayers.wumpusworld.environment.domain;
/*
 * 격자판 내의 특정 위치(X, Y)를 나타내는 불변 객체
 * */

import lombok.Value;

@Value
public class Position {
    int x;
    int y;
}
