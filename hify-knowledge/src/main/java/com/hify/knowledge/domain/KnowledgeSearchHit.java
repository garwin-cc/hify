package com.hify.knowledge.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeSearchHit extends KnowledgeChunk {

    private Double score;

    private Double vectorScore;

    private Double keywordScore;

    private Double rerankScore;

    private Double fusionScore;
}
