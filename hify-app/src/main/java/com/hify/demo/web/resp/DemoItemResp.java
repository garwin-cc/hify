package com.hify.demo.web.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DemoItemResp {

    private Long id;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
