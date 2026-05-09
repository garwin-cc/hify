package com.hify.knowledge.api;

import lombok.Data;

@Data
public class KnowledgeDocumentQuery {

    private int page = 1;

    private int size = 20;
}
