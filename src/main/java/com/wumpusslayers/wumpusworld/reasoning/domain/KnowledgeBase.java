package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;

import java.util.Arrays;
import java.util.Objects;

/**
 * 4×4 월드에 대한 에이전트 지식(관측 스냅샷 + 안전/후보 집합).
 * 환경의 숨겨진 진실({@code World}/{@code Grid})은 읽지 않는다(#12 이후 observe 파이프라인).
 * 사망 직전까지 쌓인 지식을 유지할지 여부는 호출 측 생명주기(#15)에서 결정하며, 유지 시에는 {@link #clear()}를 호출하지 않는다.
 */
public final class KnowledgeBase {

    public static final int GRID_SIZE = 4;

    private final CellBelief[][] cells;
    private final boolean[][] safe;
    private final boolean[][] possiblePit;
    private final boolean[][] possibleWumpus;

    private boolean wumpusAlive;
    private boolean heardScream;

    public KnowledgeBase() {
        this.cells = new CellBelief[GRID_SIZE][GRID_SIZE];
        this.safe = new boolean[GRID_SIZE][GRID_SIZE];
        this.possiblePit = new boolean[GRID_SIZE][GRID_SIZE];
        this.possibleWumpus = new boolean[GRID_SIZE][GRID_SIZE];
        initializeState();
    }

    private void initializeState() {
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                cells[x][y] = CellBelief.unseen();
                safe[x][y] = false;
                possiblePit[x][y] = true;
                possibleWumpus[x][y] = true;
            }
        }
        // (1,1) 시작 칸은 안전으로 가정(제안서·환경 규칙과 정합)
        safe[0][0] = true;
        possiblePit[0][0] = false;
        possibleWumpus[0][0] = false;

        this.wumpusAlive = true;
        this.heardScream = false;
    }

    /**
     * 새 게임·새 세션 등 완전 리셋이 필요할 때만 지식을 처음 상태로 되돌린다.
     * 사망 후에도 관측을 유지하는 정책에서는 호출하지 않는다(#15에서 시뮬레이션과 정합).
     */
    public void clear() {
        initializeState();
    }

    public boolean isWumpusAlive() {
        return wumpusAlive;
    }

    public boolean isHeardScream() {
        return heardScream;
    }

    /**
     * 규칙 엔진(#13)용. 안전으로 확정되면 pit/wumpus 후보를 함께 제거한다.
     */
    public void markDefinitelySafe(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        safe[xi][yi] = true;
        possiblePit[xi][yi] = false;
        possibleWumpus[xi][yi] = false;
    }

    public void setPossiblePit(Position pos, boolean value) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        possiblePit[xi][yi] = value;
        if (value && safe[xi][yi]) {
            throw new SimulationException("Invariant violated: possible pit at safe cell " + pos);
        }
    }

    public void setPossibleWumpus(Position pos, boolean value) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        possibleWumpus[xi][yi] = value;
        if (value && safe[xi][yi]) {
            throw new SimulationException("Invariant violated: possible wumpus at safe cell " + pos);
        }
    }

    public void setWumpusAlive(boolean wumpusAlive) {
        this.wumpusAlive = wumpusAlive;
    }

    public void setHeardScream(boolean heardScream) {
        this.heardScream = heardScream;
    }

    /**
     * 해당 칸에 대한 관측을 누적한다. 동일 턴·동일 percept를 반복 반영해도 값이 같으면 결과는 동일(멱등에 가깝게).
     */
    public void recordCellObservation(Position pos, Percept percept) {
        Objects.requireNonNull(percept, "percept must not be null");
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        cells[xi][yi] = cells[xi][yi].withObservation(percept);
        if (percept.isScream()) {
            this.heardScream = true;
            this.wumpusAlive = false;
        }
    }

    public boolean isValid(Position pos) {
        return pos != null
                && pos.getX() >= 1 && pos.getX() <= GRID_SIZE
                && pos.getY() >= 1 && pos.getY() <= GRID_SIZE;
    }

    public boolean isVisited(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return cells[xi][yi].visited();
    }

    public CellBelief getCellBelief(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return cells[xi][yi];
    }

    public boolean isSafe(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return safe[xi][yi];
    }

    public boolean isPossiblePit(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return possiblePit[xi][yi];
    }

    public boolean isPossibleWumpus(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return possibleWumpus[xi][yi];
    }

    /**
     * 외부에서 boolean[][] 레퍼런스를 바꾸지 못하도록 복사본을 반환한다.
     */
    public boolean[][] copySafeGrid() {
        return copyGrid(safe);
    }

    public boolean[][] copyPossiblePitGrid() {
        return copyGrid(possiblePit);
    }

    public boolean[][] copyPossibleWumpusGrid() {
        return copyGrid(possibleWumpus);
    }

    private static boolean[][] copyGrid(boolean[][] src) {
        boolean[][] dest = new boolean[GRID_SIZE][GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            dest[i] = Arrays.copyOf(src[i], GRID_SIZE);
        }
        return dest;
    }

    private int toIndexX(Position pos) {
        requireValid(pos);
        return pos.getX() - 1;
    }

    private int toIndexY(Position pos) {
        requireValid(pos);
        return pos.getY() - 1;
    }

    private void requireValid(Position pos) {
        if (!isValid(pos)) {
            throw new SimulationException("유효하지 않은 좌표입니다: " + pos);
        }
    }
}
