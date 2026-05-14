package com.hify.agent.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.domain.AgentAppPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentAppMapper extends BaseMapper<AgentAppPo> {
}
