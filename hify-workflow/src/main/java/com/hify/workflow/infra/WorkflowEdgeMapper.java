package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowEdgePo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowEdgeMapper extends BaseMapper<WorkflowEdgePo> {

    @Insert("""
            <script>
            INSERT INTO t_workflow_edge
                (workflow_id, source_node_key, target_node_key, edge_type, condition_expression, sort_order)
            VALUES
            <foreach collection="edges" item="edge" separator=",">
                (#{edge.workflowId}, #{edge.sourceNodeKey}, #{edge.targetNodeKey}, #{edge.edgeType},
                 #{edge.conditionExpression}, #{edge.sortOrder})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("edges") List<WorkflowEdgePo> edges);
}
