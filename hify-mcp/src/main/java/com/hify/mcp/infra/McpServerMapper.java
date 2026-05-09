package com.hify.mcp.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.mcp.domain.McpServerPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServerPo> {

    @Select("""
            SELECT COUNT(1)
            FROM t_agent_tool at
            INNER JOIN t_mcp_tool mt ON at.tool_id = mt.id
            WHERE mt.mcp_server_id = #{serverId}
            """)
    long countAgentBindings(@Param("serverId") Long serverId);
}
