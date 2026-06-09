package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.environment.domain.Percept;

import java.util.Objects;

/**
 * 한 칸에 대한 관측 기반 믿음(불변).
 * Stench/Breeze 의미는 {@link com.wumpusslayers.wumpusworld.environment.service.PerceptService}와 동일해야 한다(현재 칸 기준 상하좌우 인접 위협).
 */
public record CellBelief(boolean visited, Percept lastPercept) {

    /** visited와 lastPercept 조합이 모순이면 예외를 던진다. */
    public CellBelief {
        if (visited && lastPercept == null) {
            throw new IllegalArgumentException("lastPercept must not be null when visited");
        }
        if (!visited && lastPercept != null) {
            throw new IllegalArgumentException("lastPercept must be null when not visited");
        }
    }

    /** 아직 방문·관측하지 않은 칸 상태를 만든다. */
    public static CellBelief unseen() {
        return new CellBelief(false, null);
    }

    /** 이 칸에서 받은 지각으로 방문 완료 상태를 만든다(새 인스턴스). */
    public CellBelief withObservation(Percept percept) {
        Objects.requireNonNull(percept, "percept must not be null");
        return new CellBelief(true, percept);
    }
}
