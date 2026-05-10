package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowReviewTaskPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowReviewTaskMapper extends BaseMapper<WorkflowReviewTaskPo> {
}
