package com.wumpusslayers.wumpusworld.common.enums;
/*
* 에이전트의 현재 방향을 정의하고 회전(왼쪽/오른쪽 90도)
* TurnLeft, TurnRight 행동의 결과값을 결정*/

public enum Direction {
    NORTH, SOUTH, EAST, WEST;

    public Direction turnLeft() {
        return switch  (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
        };
    }

    public Direction turnRight() {
        return switch (this) {
            case NORTH -> EAST;
            case WEST -> NORTH;
            case SOUTH -> WEST;
            case EAST -> SOUTH;
        };
    }
}
