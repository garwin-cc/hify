package com.hify.common.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String providerDetail(Long id) {
        return "detail:" + id;
    }

    public static String modelEnabled(String type) {
        return "enabled:" + type;
    }

    public static String agentDetail(Long id) {
        return String.valueOf(id);
    }

    public static String mcpTools(Long serverId) {
        return "server:" + serverId + ":tools";
    }

    public static String workflowPublished(Long workflowId) {
        return "published:" + workflowId;
    }

    public static String knowledgeConfig(Long knowledgeBaseId) {
        return "kb:" + knowledgeBaseId;
    }

    public static String permission(Long userId, Long projectId) {
        return "user:" + userId + ":project:" + projectId;
    }
}
