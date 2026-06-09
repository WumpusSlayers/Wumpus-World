package com.wumpusslayers.wumpusworld.environment.dto.response;

/*
* 생성된 world의 상태를 외부에 전달하기 위한 데이터 객체
* controller가 필요하게 되면 사용 예정
* */

import com.wumpusslayers.wumpusworld.environment.domain.Position;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorldResponse {
    private Position agentPosition;
    private String agentDirection;
    private int arrowCount;
    private List<Position> pitPositions;
    private Position wumpusPosition;
    private Position goalPosition;
}
