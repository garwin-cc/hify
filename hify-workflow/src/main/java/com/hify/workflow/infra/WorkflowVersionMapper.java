package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowVersionPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionPo> {
}
