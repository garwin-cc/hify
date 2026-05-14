package com.hify.common.audit;

import lombok.Builder;
import lombok.Data;

public final class AuditContext {

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setActor(Long userId, String username) {
        ACTOR.set(Actor.builder()
                .userId(userId)
                .username(username == null ? "" : username)
                .build());
    }

    public static Actor currentActor() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }

    @Data
    @Builder
    public static class Actor {
        private Long userId;
        private String username;
    }
}
