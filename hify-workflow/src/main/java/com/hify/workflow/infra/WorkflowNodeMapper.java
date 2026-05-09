package com.hify.workflow.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.workflow.domain.WorkflowNodePo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodePo> {

    @Insert("""
            <script>
            INSERT INTO t_workflow_node
                (workflow_id, node_key, node_type, name, config, position_x, position_y)
            VALUES
            <foreach collection="nodes" item="node" separator=",">
                (#{node.workflowId}, #{node.nodeKey}, #{node.nodeType}, #{node.name},
                 #{node.config}, #{node.positionX}, #{node.positionY})
            </foreach>
            </script>
            """)
    int batchInsert(@Param("nodes") List<WorkflowNodePo> nodes);
}
