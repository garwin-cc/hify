package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowRunEventPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRunEventMapper extends BaseMapper<WorkflowRunEventPo> {

    @Select("SELECT COALESCE(MAX(event_seq), 0) FROM t_workflow_run_event WHERE workflow_run_id = #{workflowRunId} AND deleted = 0")
    Integer selectMaxEventSeq(Long workflowRunId);
}
