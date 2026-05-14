package com.hify.agent.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.agent.domain.AgentVersionPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersionPo> {
}
