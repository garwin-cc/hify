package com.hify.demo.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_demo_item")
@EqualsAndHashCode(callSuper = false)
public class DemoItemPo extends BaseEntity {

    private String name;

    private Integer status;
}
