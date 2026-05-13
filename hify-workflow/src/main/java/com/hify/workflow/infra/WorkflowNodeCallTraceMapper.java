package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowNodeCallTracePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeCallTraceMapper extends BaseMapper<WorkflowNodeCallTracePo> {
}
