package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowRunPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunPo> {
}
