package com.hify.agent.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.domain.AgentApiKeyPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentApiKeyMapper extends BaseMapper<AgentApiKeyPo> {
}
