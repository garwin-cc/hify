package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowTemplateUsagePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTemplateUsageMapper extends BaseMapper<WorkflowTemplateUsagePo> {
}
