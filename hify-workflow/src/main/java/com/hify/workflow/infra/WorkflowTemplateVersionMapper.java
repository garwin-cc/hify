package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowTemplateVersionPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTemplateVersionMapper extends BaseMapper<WorkflowTemplateVersionPo> {
}
