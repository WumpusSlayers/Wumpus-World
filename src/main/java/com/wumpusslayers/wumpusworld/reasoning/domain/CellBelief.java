package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.environment.domain.Percept;

import java.util.Objects;

/**
 * 한 칸에 대한 관측 기반 믿음(불변).
 * Stench/Breeze 의미는 {@link com.wumpusslayers.wumpusworld.environment.service.PerceptService}와 동일해야 한다(현재 칸 기준 상하좌우 인접 위협).
 */
public record CellBelief(boolean visited, Percept lastPercept) {

    public CellBelief {
        if (visited && lastPercept == null) {
            throw new IllegalArgumentException("lastPercept must not be null when visited");
        }
        if (!visited && lastPercept != null) {
            throw new IllegalArgumentException("lastPercept must be null when not visited");
        }
    }

    public static CellBelief unseen() {
        return new CellBelief(false, null);
    }

    public CellBelief withObservation(Percept percept) {
        Objects.requireNonNull(percept, "percept must not be null");
        return new CellBelief(true, percept);
    }
}
