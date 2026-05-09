package com.hify.mcp.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.mcp.domain.McpToolPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface McpToolMapper extends BaseMapper<McpToolPo> {
}
