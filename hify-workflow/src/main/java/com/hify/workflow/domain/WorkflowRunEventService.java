package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.api.WorkflowRunEventResp;
import com.hify.workflow.infra.WorkflowRunEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRunEventService implements WorkflowEventPublisher {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final String EVENT_NAME = "workflow-run-event";

    private final WorkflowRunEventMapper workflowRunEventMapper;
    private final ObjectMapper objectMapper;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publishRunEvent(Long workflowRunId, String eventType, String status, Map<String, Object> payload) {
        publish(workflowRunId, eventType, null, status, payload);
    }

    @Override
    public void publishNodeEvent(Long workflowRunId, String eventType, String nodeKey, String status, Map<String, Object> payload) {
        publish(workflowRunId, eventType, nodeKey, status, payload);
    }

    public SseEmitter subscribe(Long workflowRunId, Integer afterEventSeq, boolean runTerminal) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> removeSubscriber(workflowRunId, emitter));
        emitter.onTimeout(() -> removeSubscriber(workflowRunId, emitter));
        emitter.onError(error -> removeSubscriber(workflowRunId, emitter));

        List<WorkflowRunEventResp> missedEvents = listEventsAfter(workflowRunId, afterEventSeq);
        for (WorkflowRunEventResp event : missedEvents) {
            if (!send(emitter, event)) {
                return emitter;
            }
        }
        if (runTerminal) {
            emitter.complete();
            return emitter;
        }
        subscribers.computeIfAbsent(workflowRunId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        return emitter;
    }

    public List<WorkflowRunEventResp> listEventsAfter(Long workflowRunId, Integer afterEventSeq) {
        int after = afterEventSeq == null ? 0 : Math.max(0, afterEventSeq);
        return workflowRunEventMapper.selectList(Wrappers.lambdaQuery(WorkflowRunEventPo.class)
                        .eq(WorkflowRunEventPo::getWorkflowRunId, workflowRunId)
                        .gt(WorkflowRunEventPo::getEventSeq, after)
                        .orderByAsc(WorkflowRunEventPo::getEventSeq))
                .stream()
                .map(this::toResp)
                .toList();
    }

    private void publish(Long workflowRunId, String eventType, String nodeKey, String status, Map<String, Object> payload) {
        if (workflowRunId == null) {
            return;
        }
        WorkflowRunEventPo po = new WorkflowRunEventPo();
        po.setWorkflowRunId(workflowRunId);
        po.setEventSeq(nextSeq(workflowRunId));
        po.setEventType(eventType);
        po.setNodeKey(nodeKey);
        po.setStatus(status);
        po.setPayload(toJson(payload == null ? Collections.emptyMap() : payload));
        try {
            workflowRunEventMapper.insert(po);
            WorkflowRunEventResp resp = toResp(po);
            for (SseEmitter emitter : subscribers.getOrDefault(workflowRunId, new CopyOnWriteArrayList<>())) {
                send(emitter, resp);
            }
        } catch (Exception e) {
            log.warn("failed to publish workflow run event runId={} type={}: {}",
                    workflowRunId, eventType, e.getMessage());
        }
    }

    private synchronized int nextSeq(Long workflowRunId) {
        Integer max = workflowRunEventMapper.selectMaxEventSeq(workflowRunId);
        return (max == null ? 0 : max) + 1;
    }

    private boolean send(SseEmitter emitter, WorkflowRunEventResp event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.getEventSeq()))
                    .name(EVENT_NAME)
                    .data(event));
            return true;
        } catch (IOException | IllegalStateException e) {
            Long workflowRunId = event.getWorkflowRunId();
            removeSubscriber(workflowRunId, emitter);
            return false;
        }
    }

    private void removeSubscriber(Long workflowRunId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(workflowRunId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(workflowRunId);
        }
    }

    private WorkflowRunEventResp toResp(WorkflowRunEventPo po) {
        WorkflowRunEventResp resp = new WorkflowRunEventResp();
        resp.setId(po.getId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setEventSeq(po.getEventSeq());
        resp.setEventType(po.getEventType());
        resp.setNodeKey(po.getNodeKey());
        resp.setStatus(po.getStatus());
        resp.setPayload(parsePayload(po.getPayload()));
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private Map<String, Object> parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("failed to parse workflow event payload: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload.entrySet().stream()
                            .filter(entry -> entry.getKey() != null)
                            .filter(entry -> entry.getValue() != null)
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (left, right) -> right,
                                    java.util.LinkedHashMap::new)));
        } catch (Exception e) {
            log.warn("failed to serialize workflow event payload: {}", e.getMessage());
            return "{}";
        }
    }
}
