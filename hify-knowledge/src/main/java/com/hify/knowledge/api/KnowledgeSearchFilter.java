package com.hify.knowledge.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class KnowledgeSearchFilter {

    private String department;

    private String documentType;

    private List<String> tags;

    private LocalDateTime createdAtStart;

    private LocalDateTime createdAtEnd;

    private Long projectId;

    private String permissionScope;
}
