package com.wumpusslayers.wumpusworld.reasoning.service;

import com.wumpusslayers.wumpusworld.common.exception.SimulationException;
import com.wumpusslayers.wumpusworld.environment.domain.Percept;
import com.wumpusslayers.wumpusworld.environment.domain.Position;
import com.wumpusslayers.wumpusworld.reasoning.domain.KnowledgeBase;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션(게임) 단위 {@link KnowledgeBase}에 관측을 반영한다.
 * {@code World}/{@code Grid}는 읽지 않으며, 전달된 {@link Percept}만 사용한다(#12).
 */
@Service
public class KnowledgeUpdateService {

    private final ConcurrentHashMap<String, KnowledgeBase> knowledgeBySession = new ConcurrentHashMap<>();

    /**
     * 해당 세션의 지식에 한 칸 관측을 반영한다. 첫 호출 시 KB를 생성한다.
     * 동일 인자로 반복 호출해도 칸 상태는 동일하게 유지된다(멱등).
     */
    public void observe(String sessionId, Position position, Percept percept) {
        if (sessionId == null) {
            throw new SimulationException("sessionId must not be null");
        }
        if (position == null) {
            throw new SimulationException("position must not be null");
        }
        if (percept == null) {
            throw new SimulationException("percept must not be null");
        }
        knowledgeBySession
                .computeIfAbsent(sessionId, id -> new KnowledgeBase())
                .recordCellObservation(position, percept);

        // 테스트용 로그 출력 (관측 정보는 Cyan 색상으로 강조 출력)
        System.out.println("📝 [KB 업데이트] 좌표: " + position + " | 관측: \u001B[36m" + percept + "\u001B[0m");
    }

    /**
     * 세션에 연결된 KB를 반환한다. 없으면 null.
     */
    public KnowledgeBase getKnowledgeBaseOrNull(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return knowledgeBySession.get(sessionId);
    }

    /**
     * 세션 지식을 제거한다. 동일 {@code sessionId}로 새 게임을 시작하기 전에 호출하는 것을 권장한다.
     */
    public void resetSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        knowledgeBySession.remove(sessionId);
    }
}
