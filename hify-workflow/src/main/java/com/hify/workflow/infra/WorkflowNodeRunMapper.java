package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowNodeRunPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunPo> {
}
