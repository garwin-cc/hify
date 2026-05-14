package com.hify.model.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.model.domain.LlmCallStatPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmCallStatMapper extends BaseMapper<LlmCallStatPo> {
}
