package com.hify.app.domain;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateQualitySampleStatusReq {

    private String reviewStatus;

    @Size(max = 1000)
    private String resolutionNote;
}
