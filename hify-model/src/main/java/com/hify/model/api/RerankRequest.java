package com.hify.model.api;

import lombok.Data;

import java.util.List;

@Data
public class RerankRequest {

    private Long modelConfigId;

    private String query;

    private List<String> documents;

    private Integer topN;
}
