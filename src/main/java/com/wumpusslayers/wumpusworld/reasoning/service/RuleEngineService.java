package com.wumpusslayers.wumpusworld.reasoning.service;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.InferenceRule;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link KnowledgeBase}에 저장된 관측만으로 Pit·Wumpus 후보를 줄이는 전진 추론(#13·#19).
 * 환경의 숨겨진 진실({@code World}/{@code Grid})은 읽지 않는다.
 */
@Service
public class RuleEngineService {

    private static final int MAX_ROUNDS = 64;

    private final Logger log = LoggerFactory.getLogger(RuleEngineService.class);

    /**
     * KB가 더 이상 바뀌지 않을 때까지 규칙을 반복 적용한다. 상한에 도달하면 경고 로그 후 중단한다.
     */
    public void runInference(KnowledgeBase kb) {
        if (kb == null) {
            throw new SimulationException("KnowledgeBase must not be null");
        }
        int round = 0;
        boolean changed;
        do {
            changed = applyOneRound(kb);
            round++;
            if (changed && log.isDebugEnabled()) {
                log.debug("reasoning round {} applied changes", round);
            }
        } while (changed && round < MAX_ROUNDS);

        if (changed) {
            log.warn("Rule engine stopped after {} rounds while KB was still changing", MAX_ROUNDS);
        }
    }

    /** 한 라운드: 방문·전멸 시 움퍼스 후보 제거·바람·악취 규칙, breeze/pit·stench/wumpus 단일 후보 축소, 후보 소진 시 안전 확정(#13·#19). */
    private boolean applyOneRound(KnowledgeBase kb) {
        Map<InferenceRule, Boolean> fired = log.isDebugEnabled() ? new EnumMap<>(InferenceRule.class) : null;

        boolean changed = false;
        changed |= applyVisitedCellsAreSafe(kb);
        changed |= applyRule(kb, InferenceRule.SCREAM_WUMPUS_ELIMINATED, fired, this::applyScreamEliminatesWumpusCandidates);
        changed |= applyRule(kb, InferenceRule.NO_BREEZE_CLEAR_ADJACENT_PIT_CANDIDATES, fired, this::applyNoBreezeClearsAdjacentPit);
        changed |= applyRule(kb, InferenceRule.NO_STENCH_CLEAR_ADJACENT_WUMPUS_CANDIDATES, fired, this::applyNoStenchClearsAdjacentWumpus);
        changed |= applyRule(kb, InferenceRule.BREEZE_MARK_PIT_CANDIDATES, fired, this::applyBreezeMarksAdjacentPitCandidates);
        changed |= applyRule(kb, InferenceRule.BREEZE_PIT_SINGLETON_NARROWS_NEIGHBORS, fired, this::applyBreezePitSingletonNarrowsNeighbors);
        changed |= applyRule(kb, InferenceRule.BREEZE_UNIQUE_UNSAFE_NEIGHBOR_IDENTIFIES_PIT, fired, this::applyBreezeIdentifiesUniqueUnsafeNeighborAsPit);
        changed |= applyRule(kb, InferenceRule.STENCH_MARK_WUMPUS_CANDIDATES, fired, this::applyStenchMarksAdjacentWumpusCandidates);
        changed |= applyRule(kb, InferenceRule.STENCH_WUMPUS_SINGLETON_NARROWS_NEIGHBORS, fired, this::applyStenchWumpusSingletonNarrowsNeighbors);
        changed |= applyRule(kb, InferenceRule.STENCH_UNIQUE_UNSAFE_NEIGHBOR_IDENTIFIES_WUMPUS, fired, this::applyStenchIdentifiesUniqueUnsafeNeighborAsWumpus);
        changed |= applyRule(kb, InferenceRule.CONFIRMED_PIT_CLEARS_WUMPUS_CANDIDATE, fired, this::applyConfirmedPitClearsWumpusCandidate);
        changed |= applyRule(kb, InferenceRule.DEFINITE_PIT_EXPLAINS_BREEZE, fired, this::applyDefinitePitExplainsAdjacentBreeze);
        changed |= applyRule(kb, InferenceRule.DEFINITE_WUMPUS_EXPLAINS_STENCH, fired, this::applyDefiniteWumpusExplainsAdjacentStench);
        changed |= applyRule(kb, InferenceRule.CONFIRMED_WUMPUS_CLEARS_PIT_CANDIDATE, fired, this::applyConfirmedWumpusClearsPitCandidate);
        changed |= applyCandidateFreeCellsAsSafe(kb);

        if (fired != null && !fired.isEmpty()) {
            log.debug("rules touched this sub-pass: {}", fired);
        }
        return changed;
    }

    /** {@link #applyRule}에서 한 번 호출할 규칙 적용 블록. */
    @FunctionalInterface
    private interface RuleApplier {
        boolean apply(KnowledgeBase kb);
    }

    /** 단일 규칙을 적용하고, debug 시 어떤 규칙이 바꿨는지 기록한다. */
    private boolean applyRule(
            KnowledgeBase kb,
            InferenceRule rule,
            Map<InferenceRule, Boolean> fired,
            RuleApplier applier
    ) {
        boolean delta = applier.apply(kb);
        if (delta && fired != null) {
            fired.put(rule, true);
        }
        return delta;
    }

    /** 방문한 칸은 생존했으므로 pit·wumpus가 없다고 본다. Pit/Wumpus 확정 칸은 스킵(#34·#37). */
    private boolean applyVisitedCellsAreSafe(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isVisited(p) || kb.isDefinitePit(p) || kb.isDefiniteWumpus(p)) {
                    continue;
                }
                if (!kb.isSafe(p) || kb.isPossiblePit(p) || kb.isPossibleWumpus(p)) {
                    kb.markDefinitelySafe(p);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * KB에서 살아 있는 움퍼스가 없다고 판정되면({@link KnowledgeBase#isWumpusAlive()} false) 전 격자에서 움퍼스 후보를 제거한다.
     * 비명(percept)만으로는 생존 플래그를 끄지 않으므로, 다중 움퍼스에서는 시뮬 등이 플래그를 맞춘 뒤 이 규칙이 동작한다.
     * {@link InferenceRule#SCREAM_WUMPUS_ELIMINATED} 식별자와 연결되나, 메서드명은 이력적 표기다.
     */
    private boolean applyScreamEliminatesWumpusCandidates(KnowledgeBase kb) {
        if (kb.isWumpusAlive()) {
            return false;
        }
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (kb.isDefiniteWumpus(p)) {
                    continue;
                }
                if (kb.isPossibleWumpus(p)) {
                    kb.setPossibleWumpus(p, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 바람이 없는 관측 칸: 인접 칸의 pit 후보를 제거한다. */
    private boolean applyNoBreezeClearsAdjacentPit(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isVisited(p) || kb.getCellBelief(p).lastPercept().isBreeze()) {
                    continue;
                }
                for (Position n : neighbors(p)) {
                    if (!kb.isValid(n) || kb.isDefinitePit(n)) {
                        continue;
                    }
                    if (kb.isPossiblePit(n)) {
                        kb.setPossiblePit(n, false);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** 악취가 없는 관측 칸: 인접 칸의 wumpus 후보를 제거한다. Wumpus 확정 칸은 스킵(#37). */
    private boolean applyNoStenchClearsAdjacentWumpus(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isVisited(p) || kb.getCellBelief(p).lastPercept().isStench()) {
                    continue;
                }
                for (Position n : neighbors(p)) {
                    if (!kb.isValid(n) || kb.isDefiniteWumpus(n)) {
                        continue;
                    }
                    if (kb.isPossibleWumpus(n)) {
                        kb.setPossibleWumpus(n, false);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** 바람이 있는 관측 칸: 안전·Wumpus 확정이 아닌 인접 칸에 pit 후보를 표시한다(#37). */
    private boolean applyBreezeMarksAdjacentPitCandidates(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isVisited(p) || !kb.getCellBelief(p).lastPercept().isBreeze()) {
                    continue;
                }
                boolean breezeExplainedByDefinitePit = false;
                for (Position n : neighbors(p)) {
                    if (kb.isValid(n) && kb.isDefinitePit(n)) {
                        breezeExplainedByDefinitePit = true;
                        break;
                    }
                }
                if (breezeExplainedByDefinitePit) {
                    continue;
                }
                for (Position n : neighbors(p)) {
                    if (!kb.isValid(n) || kb.isSafe(n) || kb.isDefiniteWumpus(n) || kb.isDefinitePit(n)) {
                        continue;
                    }
                    if (isRuledOutAsPitByNoBreezeAtAdjacentVisitedCell(kb, n)) {
                        continue;
                    }
                    if (!kb.isPossiblePit(n)) {
                        kb.setPossiblePit(n, true);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** 악취가 있고 움퍼스가 살아 있으면: 안전·Pit 확정이 아닌 인접 칸에 wumpus 후보를 표시한다. */
    private boolean applyStenchMarksAdjacentWumpusCandidates(KnowledgeBase kb) {
        if (!kb.isWumpusAlive()) {
            return false;
        }
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isVisited(p) || !kb.getCellBelief(p).lastPercept().isStench()) {
                    continue;
                }
                boolean stenchExplainedByDefiniteWumpus = false;
                for (Position n : neighbors(p)) {
                    if (kb.isValid(n) && kb.isDefiniteWumpus(n)) {
                        stenchExplainedByDefiniteWumpus = true;
                        break;
                    }
                }
                if (stenchExplainedByDefiniteWumpus) {
                    continue;
                }
                for (Position n : neighbors(p)) {
                    if (!kb.isValid(n) || kb.isSafe(n) || kb.isDefinitePit(n) || kb.isDefiniteWumpus(n)) {
                        continue;
                    }
                    if (isRuledOutAsWumpusByNoStenchAtAdjacentVisitedCell(kb, n)) {
                        continue;
                    }
                    if (!kb.isPossibleWumpus(n)) {
                        kb.setPossibleWumpus(n, true);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * 방문한 무악취 칸의 이웃이면 wumpus 후보가 될 수 없다(#13·#39).
     * 다른 stench 칸이 후보를 다시 켜는 진동을 막는다.
     */
    private static boolean isRuledOutAsWumpusByNoStenchAtAdjacentVisitedCell(KnowledgeBase kb, Position candidate) {
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position observed = new Position(x, y);
                if (!kb.isVisited(observed) || kb.getCellBelief(observed).lastPercept().isStench()) {
                    continue;
                }
                for (Position n : neighbors(observed)) {
                    if (n.equals(candidate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 방문한 무풍 칸의 이웃이면 pit 후보가 될 수 없다(#13·#39).
     * 다른 breeze 칸이 후보를 다시 켜는 진동을 막는다.
     */
    private static boolean isRuledOutAsPitByNoBreezeAtAdjacentVisitedCell(KnowledgeBase kb, Position candidate) {
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position observed = new Position(x, y);
                if (!kb.isVisited(observed) || kb.getCellBelief(observed).lastPercept().isBreeze()) {
                    continue;
                }
                for (Position n : neighbors(observed)) {
                    if (n.equals(candidate)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Breeze가 난 방문 칸마다, 인접 중 “안전이 아니면서 pit 후보”인 칸이 하나뿐이면
     * 같은 인접의 나머지 칸은 pit 후보에서 제외한다(#19).
     */
    private boolean applyBreezePitSingletonNarrowsNeighbors(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position b = new Position(x, y);
                if (!kb.isVisited(b) || !kb.getCellBelief(b).lastPercept().isBreeze()) {
                    continue;
                }
                List<Position> pitCandidates = new ArrayList<>();
                for (Position n : neighbors(b)) {
                    if (!kb.isValid(n) || kb.isSafe(n)) {
                        continue;
                    }
                    if (kb.isPossiblePit(n)) {
                        pitCandidates.add(n);
                    }
                }
                if (pitCandidates.size() != 1) {
                    continue;
                }
                Position only = pitCandidates.get(0);
                for (Position m : neighbors(b)) {
                    if (!kb.isValid(m) || m.equals(only) || kb.isDefinitePit(m)) {
                        continue;
                    }
                    if (kb.isPossiblePit(m)) {
                        kb.setPossiblePit(m, false);
                        changed = true;
                    }
                }
                // definiteWumpus 칸이 pit 후보로 잡혔다면 정합 위반이므로 정리는 다음 룰(CONFIRMED_WUMPUS_CLEARS_PIT_CANDIDATE)이 맡음(#37).
            }
        }
        return changed;
    }

    /**
     * Stench가 난 방문 칸마다, 인접 중 “안전이 아니면서 wumpus 후보”인 칸이 하나뿐이면
     * 같은 인접의 나머지 칸은 wumpus 후보에서 제외한다(#19).
     */
    private boolean applyStenchWumpusSingletonNarrowsNeighbors(KnowledgeBase kb) {
        if (!kb.isWumpusAlive()) {
            return false;
        }
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position s = new Position(x, y);
                if (!kb.isVisited(s) || !kb.getCellBelief(s).lastPercept().isStench()) {
                    continue;
                }
                List<Position> wumpusCandidates = new ArrayList<>();
                for (Position n : neighbors(s)) {
                    if (!kb.isValid(n) || kb.isSafe(n)) {
                        continue;
                    }
                    if (kb.isPossibleWumpus(n)) {
                        wumpusCandidates.add(n);
                    }
                }
                if (wumpusCandidates.size() != 1) {
                    continue;
                }
                Position only = wumpusCandidates.get(0);
                for (Position m : neighbors(s)) {
                    if (!kb.isValid(m) || m.equals(only) || kb.isDefiniteWumpus(m)) {
                        continue;
                    }
                    if (kb.isPossibleWumpus(m)) {
                        kb.setPossibleWumpus(m, false);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Breeze 칸 인접 중 안전이 아닌 칸이 하나뿐이면 그 칸을 Pit으로 확정한다(#39).
     */
    private boolean applyBreezeIdentifiesUniqueUnsafeNeighborAsPit(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position b = new Position(x, y);
                if (!kb.isVisited(b) || !kb.getCellBelief(b).lastPercept().isBreeze()) {
                    continue;
                }
                boolean breezeExplainedByDefinitePit = false;
                for (Position n : neighbors(b)) {
                    if (kb.isValid(n) && kb.isDefinitePit(n)) {
                        breezeExplainedByDefinitePit = true;
                        break;
                    }
                }
                if (breezeExplainedByDefinitePit) {
                    continue;
                }
                Position onlyUnsafe = null;
                int unsafeCount = 0;
                for (Position n : neighbors(b)) {
                    if (!kb.isValid(n) || kb.isDefinitePit(n) || kb.isDefiniteWumpus(n)) {
                        continue;
                    }
                    if (!kb.isSafe(n)) {
                        unsafeCount++;
                        onlyUnsafe = n;
                    }
                }
                if (unsafeCount != 1 || onlyUnsafe == null || kb.isDefinitePit(onlyUnsafe)) {
                    continue;
                }
                kb.markDefinitePit(onlyUnsafe);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Stench 칸 인접 중 안전이 아닌 칸이 하나뿐이면 그 칸을 Wumpus로 확정한다(#39).
     */
    private boolean applyStenchIdentifiesUniqueUnsafeNeighborAsWumpus(KnowledgeBase kb) {
        if (!kb.isWumpusAlive()) {
            return false;
        }
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position s = new Position(x, y);
                if (!kb.isVisited(s) || !kb.getCellBelief(s).lastPercept().isStench()) {
                    continue;
                }
                boolean stenchExplainedByDefiniteWumpus = false;
                for (Position n : neighbors(s)) {
                    if (kb.isValid(n) && kb.isDefiniteWumpus(n)) {
                        stenchExplainedByDefiniteWumpus = true;
                        break;
                    }
                }
                if (stenchExplainedByDefiniteWumpus) {
                    continue;
                }
                Position onlyUnsafe = null;
                int unsafeCount = 0;
                for (Position n : neighbors(s)) {
                    if (!kb.isValid(n) || kb.isDefinitePit(n) || kb.isDefiniteWumpus(n)) {
                        continue;
                    }
                    if (!kb.isSafe(n)) {
                        unsafeCount++;
                        onlyUnsafe = n;
                    }
                }
                if (unsafeCount != 1 || onlyUnsafe == null || kb.isDefiniteWumpus(onlyUnsafe)) {
                    continue;
                }
                kb.markDefiniteWumpus(onlyUnsafe);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Breeze 칸 인접에 Pit 확정이 있고, 그 breeze의 "미해결" 이웃(안전·확정 pit 제외)이 하나뿐이면
     * 그 이웃의 pit 후보만 제거한다(#39, 다중 pit).
     * 인접에 pit 후보가 여러 개 남는 breeze(예: (3,3) breeze + (4,3)! 만으로 (3,4) 제거)는 건드리지 않는다.
     */
    private boolean applyDefinitePitExplainsAdjacentBreeze(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position b = new Position(x, y);
                if (!kb.isVisited(b) || !kb.getCellBelief(b).lastPercept().isBreeze()) {
                    continue;
                }
                boolean hasDefinitePitNeighbor = false;
                for (Position n : neighbors(b)) {
                    if (kb.isValid(n) && kb.isDefinitePit(n)) {
                        hasDefinitePitNeighbor = true;
                        break;
                    }
                }
                if (!hasDefinitePitNeighbor) {
                    continue;
                }
                Position soleOpenNeighbor = null;
                int openCount = 0;
                for (Position m : neighbors(b)) {
                    if (!kb.isValid(m) || kb.isSafe(m) || kb.isDefinitePit(m)) {
                        continue;
                    }
                    openCount++;
                    soleOpenNeighbor = m;
                }
                if (openCount != 1 || soleOpenNeighbor == null) {
                    continue;
                }
                if (kb.isPossiblePit(soleOpenNeighbor)) {
                    kb.setPossiblePit(soleOpenNeighbor, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Stench 칸 인접에 Wumpus 확정이 있고, 미해결 이웃이 하나뿐이면 그 이웃의 wumpus 후보만 제거한다(#39, 다중 wumpus).
     */
    private boolean applyDefiniteWumpusExplainsAdjacentStench(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position s = new Position(x, y);
                if (!kb.isVisited(s) || !kb.getCellBelief(s).lastPercept().isStench()) {
                    continue;
                }
                boolean hasDefiniteWumpusNeighbor = false;
                for (Position n : neighbors(s)) {
                    if (kb.isValid(n) && kb.isDefiniteWumpus(n)) {
                        hasDefiniteWumpusNeighbor = true;
                        break;
                    }
                }
                if (!hasDefiniteWumpusNeighbor) {
                    continue;
                }
                Position soleOpenNeighbor = null;
                int openCount = 0;
                for (Position m : neighbors(s)) {
                    if (!kb.isValid(m) || kb.isSafe(m) || kb.isDefiniteWumpus(m)) {
                        continue;
                    }
                    openCount++;
                    soleOpenNeighbor = m;
                }
                if (openCount != 1 || soleOpenNeighbor == null) {
                    continue;
                }
                if (kb.isPossibleWumpus(soleOpenNeighbor)) {
                    kb.setPossibleWumpus(soleOpenNeighbor, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Pit으로 확정된 칸은 wumpus 후보에서 제거한다(#34). */
    private boolean applyConfirmedPitClearsWumpusCandidate(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (kb.isDefinitePit(p) && kb.isPossibleWumpus(p)) {
                    kb.setPossibleWumpus(p, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Wumpus로 확정된 칸은 pit 후보에서 제거한다(#37, 상호 배제). */
    private boolean applyConfirmedWumpusClearsPitCandidate(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (kb.isDefiniteWumpus(p) && kb.isPossiblePit(p)) {
                    kb.setPossiblePit(p, false);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * pit·wumpus 후보가 모두 없고, 인접에 방문된(=무풍·무악취가 정리해 준) 칸이 적어도 하나 있으면 안전으로 확정한다(#39).
     * prior가 빈 상태에서는 "후보 없음"이 곧 "정보 없음"과 같으므로, 관측 근거가 닿은 칸만 safe로 본다.
     */
    private boolean applyCandidateFreeCellsAsSafe(KnowledgeBase kb) {
        boolean changed = false;
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position p = new Position(x, y);
                if (!kb.isValid(p) || kb.isSafe(p)) {
                    continue;
                }
                if (kb.isPossiblePit(p) || kb.isPossibleWumpus(p)) {
                    continue;
                }
                if (mayStillHidePitOrWumpusFromAdjacentBreezeOrStench(kb, p)) {
                    continue;
                }
                boolean hasVisitedNeighbor = false;
                for (Position n : neighbors(p)) {
                    if (kb.isValid(n) && kb.isVisited(n)) {
                        hasVisitedNeighbor = true;
                        break;
                    }
                }
                if (!hasVisitedNeighbor) {
                    continue;
                }
                kb.markDefinitelySafe(p);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * breeze/stench 칸 옆에 아직 "미해결" 이웃(안전·확정 제외)이 하나 더 있으면,
     * 후보 없음만으로 safe 처리하면 안 된다(#39, 다중 pit/wumpus).
     */
    private static boolean mayStillHidePitOrWumpusFromAdjacentBreezeOrStench(KnowledgeBase kb, Position p) {
        for (int x = 1; x <= KnowledgeBase.GRID_SIZE; x++) {
            for (int y = 1; y <= KnowledgeBase.GRID_SIZE; y++) {
                Position observed = new Position(x, y);
                if (!kb.isVisited(observed)) {
                    continue;
                }
                boolean breeze = kb.getCellBelief(observed).lastPercept().isBreeze();
                boolean stench = kb.getCellBelief(observed).lastPercept().isStench();
                if (!breeze && !stench) {
                    continue;
                }
                if (!isNeighborOf(observed, p)) {
                    continue;
                }
                for (Position other : neighbors(observed)) {
                    if (other.equals(p)) {
                        continue;
                    }
                    if (!kb.isValid(other) || kb.isSafe(other)) {
                        continue;
                    }
                    if (breeze && !kb.isDefinitePit(other)) {
                        return true;
                    }
                    if (stench && kb.isWumpusAlive() && !kb.isDefiniteWumpus(other)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isNeighborOf(Position center, Position candidate) {
        return neighbors(center).contains(candidate);
    }

    /** 4×4 격자에서 상하좌우 인접 좌표만 반환한다. */
    static List<Position> neighbors(Position p) {
        List<Position> out = new ArrayList<>(4);
        int x = p.getX();
        int y = p.getY();
        if (x > 1) {
            out.add(new Position(x - 1, y));
        }
        if (x < KnowledgeBase.GRID_SIZE) {
            out.add(new Position(x + 1, y));
        }
        if (y > 1) {
            out.add(new Position(x, y - 1));
        }
        if (y < KnowledgeBase.GRID_SIZE) {
            out.add(new Position(x, y + 1));
        }
        return out;
    }

    /** 우선순위 순으로 규칙 식별자를 나열한다(테스트·문서용). */
    static List<InferenceRule> rulesInDefaultOrder() {
        return java.util.Arrays.stream(InferenceRule.values())
                .sorted(Comparator.comparingInt(InferenceRule::defaultPriority))
                .toList();
    }
}
