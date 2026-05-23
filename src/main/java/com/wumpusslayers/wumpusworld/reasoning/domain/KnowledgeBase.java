package com.wumpusslayers.wumpusworld.reasoning.domain;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;

import java.util.Arrays;
import java.util.Objects;

/**
 * 4×4 월드에 대한 에이전트 지식(관측 스냅샷 + 안전/후보 집합).
 * 환경의 숨겨진 진실({@code World}/{@code Grid})은 읽지 않는다(#12 이후 observe 파이프라인).
 * 초기 prior는 "미지 = 후보 없음"으로, (1,1)만 safe이고 그 외 칸의 {@code possiblePit}/{@code possibleWumpus}는 false다.
 * 후보는 시뮬이 percept를 흘려주면 {@link com.wumpusslayers.wumpusworld.reasoning.service.RuleEngineService}가 신호 칸의 인접에만 켠다(#13·#39).
 * 사망 직전까지 쌓인 지식을 유지할지 여부는 호출 측 생명주기(#15)에서 결정하며, 유지 시에는 {@link #clear()}를 호출하지 않는다.
 */
public final class KnowledgeBase {

    public static final int GRID_SIZE = 4;

    private final CellBelief[][] cells;
    private final boolean[][] safe;
    private final boolean[][] possiblePit;
    private final boolean[][] possibleWumpus;
    private final boolean[][] definitePit;
    private final boolean[][] definiteWumpus;
    /** 화살이 통과하여 Wumpus가 없음이 확인된 칸. stench 규칙에서 후보 재등록 방지용. */
    private boolean[][] arrowCleared;

    private boolean wumpusAlive;
    private boolean heardScream;

    /** 4×4 격자 지식을 초기 상태(시작칸 안전 등)로 만든다. */
    public KnowledgeBase() {
        this.cells = new CellBelief[GRID_SIZE][GRID_SIZE];
        this.safe = new boolean[GRID_SIZE][GRID_SIZE];
        this.possiblePit = new boolean[GRID_SIZE][GRID_SIZE];
        this.possibleWumpus = new boolean[GRID_SIZE][GRID_SIZE];
        this.definitePit = new boolean[GRID_SIZE][GRID_SIZE];
        this.definiteWumpus = new boolean[GRID_SIZE][GRID_SIZE];
        /** 화살 통과 칸 초기화 */
        this.arrowCleared = new boolean[GRID_SIZE][GRID_SIZE];
        initializeState();
    }

    private void initializeState() {
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                cells[x][y] = CellBelief.unseen();
                safe[x][y] = false;
                // 신호(breeze/stench) 관측 전에는 후보를 깔지 않는다(#39).
                // breeze/stench가 들어오면 RuleEngineService의 인접 등록 룰(#13)이 켠다.
                possiblePit[x][y] = false;
                possibleWumpus[x][y] = false;
                definitePit[x][y] = false;
                definiteWumpus[x][y] = false;
            }
        }
        // (1,1) 시작 칸은 안전으로 가정(제안서·환경 규칙과 정합)
        safe[0][0] = true;

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

    /** KB가 아는 한 맵에 살아 있는 움퍼스가 하나라도 있는지. 비명(percept)만으로는 끄지 않고, 시뮬 등이 {@link #setWumpusAlive(boolean)}로 맞춘다. */
    public boolean isWumpusAlive() {
        return wumpusAlive;
    }

    /** Scream을 들었는지 여부. */
    public boolean isHeardScream() {
        return heardScream;
    }

    /**
     * 규칙 엔진(#13)용. 안전으로 확정되면 pit/wumpus 후보를 함께 제거한다.
     * Pit/Wumpus으로 확정된 칸을 안전으로 표시하면 불변식 위반이다(#34·#37).
     */
    public void markDefinitelySafe(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        if (definitePit[xi][yi]) {
            throw new SimulationException("Invariant violated: safe at definite pit cell " + pos);
        }
        if (definiteWumpus[xi][yi]) {
            throw new SimulationException("Invariant violated: safe at definite wumpus cell " + pos);
        }
        safe[xi][yi] = true;
        possiblePit[xi][yi] = false;
        possibleWumpus[xi][yi] = false;
    }

    /**
     * 해당 칸을 Pit으로 확정한다(예: 에이전트가 그 칸에서 사망). 안전·Wumpus 확정 칸이면 예외.
     * possiblePit은 true, possibleWumpus는 false로 정합한다(#34).
     */
    public void markDefinitePit(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        if (safe[xi][yi]) {
            throw new SimulationException("Invariant violated: definite pit at safe cell " + pos);
        }
        if (definiteWumpus[xi][yi]) {
            throw new SimulationException("Invariant violated: definite pit at definite wumpus cell " + pos);
        }
        definitePit[xi][yi] = true;
        possiblePit[xi][yi] = true;
        possibleWumpus[xi][yi] = false;
    }

    /**
     * 해당 칸을 Wumpus로 확정한다(에이전트가 그 칸에서 Wumpus에 사망). 안전·Pit 확정 칸이면 예외.
     * possibleWumpus는 true, possiblePit은 false로 정합한다(#37).
     */
    public void markDefiniteWumpus(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        if (safe[xi][yi]) {
            throw new SimulationException("Invariant violated: definite wumpus at safe cell " + pos);
        }
        if (definitePit[xi][yi]) {
            throw new SimulationException("Invariant violated: definite wumpus at definite pit cell " + pos);
        }
        definiteWumpus[xi][yi] = true;
        possibleWumpus[xi][yi] = true;
        possiblePit[xi][yi] = false;
    }

    /** 해당 칸에 pit이 있을 수 있는지 설정한다. 안전·Pit 확정 칸에 true면 예외, 확정 Pit에 false면 예외. */
    public void setPossiblePit(Position pos, boolean value) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        if (!value && definitePit[xi][yi]) {
            throw new SimulationException("Invariant violated: cannot clear possible pit at definite pit cell " + pos);
        }
        possiblePit[xi][yi] = value;
        if (value && safe[xi][yi]) {
            throw new SimulationException("Invariant violated: possible pit at safe cell " + pos);
        }
    }

    /** 해당 칸에 움퍼스가 있을 수 있는지 설정한다. 안전 칸에 true면 예외, Wumpus 확정 칸에 false면 예외(#37). */
    public void setPossibleWumpus(Position pos, boolean value) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        if (!value && definiteWumpus[xi][yi]) {
            throw new SimulationException("Invariant violated: cannot clear possible wumpus at definite wumpus cell " + pos);
        }
        possibleWumpus[xi][yi] = value;
        if (value && safe[xi][yi]) {
            throw new SimulationException("Invariant violated: possible wumpus at safe cell " + pos);
        }
    }

    /**
     * 맵에 살아 있는 움퍼스가 하나라도 있다고 볼지 여부를 설정한다.
     * 시뮬이 격자 진실에 맞춰 갱신할 때(예: {@link com.wumpusslayers.wumpusworld.reasoning.service.ReasoningService#syncWumpusAlive(String, boolean)})와 같이 호출되며,
     * 비명(percept)만으로는 바뀌지 않는다. 이후 {@link com.wumpusslayers.wumpusworld.reasoning.service.RuleEngineService}가 후보 격자를 정리한다.
     */
    public void setWumpusAlive(boolean wumpusAlive) {
        this.wumpusAlive = wumpusAlive;
    }

    /** Scream 들음 여부를 직접 설정한다. */
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
        }
    }

    /** 격자 안의 좌표(1~4)인지 여부. null이면 false. */
    public boolean isValid(Position pos) {
        return pos != null
                && pos.getX() >= 1 && pos.getX() <= GRID_SIZE
                && pos.getY() >= 1 && pos.getY() <= GRID_SIZE;
    }

    /** 해당 칸을 방문해 관측이 기록되었는지. */
    public boolean isVisited(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return cells[xi][yi].visited();
    }

    /** 해당 칸의 믿음(방문·마지막 지각)을 반환한다. */
    public CellBelief getCellBelief(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return cells[xi][yi];
    }

    /** 추론상 안전으로 확정된 칸인지. */
    public boolean isSafe(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return safe[xi][yi];
    }

    /** 해당 칸에 pit이 있을 수 있다고 보는지. */
    public boolean isPossiblePit(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return possiblePit[xi][yi];
    }

    /** 해당 칸에 움퍼스가 있을 수 있다고 보는지. */
    public boolean isPossibleWumpus(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return possibleWumpus[xi][yi];
    }

    /** 해당 칸이 화살 통과로 Wumpus 없음이 확인됐는지 반환한다. */
    public boolean isArrowCleared(Position pos) {
        return arrowCleared[toIndexX(pos)][toIndexY(pos)];
    }

    /** 화살 통과로 Wumpus 없음이 확인된 칸으로 표시한다. */
    public void setArrowCleared(Position pos) {
        arrowCleared[toIndexX(pos)][toIndexY(pos)] = true;
    }

    /** 해당 칸이 Pit으로 100% 확정되었는지. */
    public boolean isDefinitePit(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return definitePit[xi][yi];
    }

    /** 해당 칸이 Wumpus로 100% 확정되었는지(#37). */
    public boolean isDefiniteWumpus(Position pos) {
        int xi = toIndexX(pos);
        int yi = toIndexY(pos);
        return definiteWumpus[xi][yi];
    }

    /**
     * 외부에서 boolean[][] 레퍼런스를 바꾸지 못하도록 복사본을 반환한다.
     */
    public boolean[][] copySafeGrid() {
        return copyGrid(safe);
    }

    /** pit 후보 그리드의 복사본을 반환한다. */
    public boolean[][] copyPossiblePitGrid() {
        return copyGrid(possiblePit);
    }

    /** 움퍼스 후보 그리드의 복사본을 반환한다. */
    public boolean[][] copyPossibleWumpusGrid() {
        return copyGrid(possibleWumpus);
    }

    /** Pit 확정 그리드의 복사본을 반환한다. */
    public boolean[][] copyDefinitePitGrid() {
        return copyGrid(definitePit);
    }

    /** Wumpus 확정 그리드의 복사본을 반환한다(#37). */
    public boolean[][] copyDefiniteWumpusGrid() {
        return copyGrid(definiteWumpus);
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

    /** 콘솔에 현재 지식베이스 상태를 4x4 격자로 시각화하여 출력 */
    public void printState() {
        System.out.println("==================================================");
        System.out.println("🧠 [KnowledgeBase Snapshot]");
        for (int y = GRID_SIZE; y >= 1; y--) {
            StringBuilder row = new StringBuilder();
            for (int x = 1; x <= GRID_SIZE; x++) {
                int xi = x - 1;
                int yi = y - 1;
                String v = cells[xi][yi].visited() ? "V" : " ";
                String s = safe[xi][yi] ? "S" : " ";
                String p = definitePit[xi][yi] ? "!" : (possiblePit[xi][yi] ? "P" : " ");
                String w = definiteWumpus[xi][yi] ? "@" : (possibleWumpus[xi][yi] ? "W" : " ");
                row.append(String.format("(%d,%d)[%s%s%s%s]   ", x, y, v, s, p, w));
            }
            System.out.println(row.toString());
        }
        System.out.println("범례 - V:방문, S:안전, P:pit후보, !:pit확정, W:wumpus후보, @:wumpus확정");
        System.out.println("==================================================");
    }
}
