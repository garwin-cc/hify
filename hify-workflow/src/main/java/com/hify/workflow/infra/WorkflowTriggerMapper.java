package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowTriggerPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTriggerMapper extends BaseMapper<WorkflowTriggerPo> {
}
