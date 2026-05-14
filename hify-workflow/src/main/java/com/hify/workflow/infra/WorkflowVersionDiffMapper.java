package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowVersionDiffPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowVersionDiffMapper extends BaseMapper<WorkflowVersionDiffPo> {
}
