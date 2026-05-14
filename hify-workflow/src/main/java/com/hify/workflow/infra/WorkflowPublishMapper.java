package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowPublishPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowPublishMapper extends BaseMapper<WorkflowPublishPo> {
}
