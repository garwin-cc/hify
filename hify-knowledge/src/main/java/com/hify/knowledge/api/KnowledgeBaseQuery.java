package com.hify.knowledge.api;

import lombok.Data;

@Data
public class KnowledgeBaseQuery {

    private int page = 1;

    private int size = 20;

    private String name;

    private Long projectId;
}
