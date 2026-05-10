package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowTemplatePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTemplateMapper extends BaseMapper<WorkflowTemplatePo> {
}
