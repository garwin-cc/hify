package com.hify.knowledge.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.knowledge.domain.RagRetrievalTracePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RagRetrievalTraceMapper extends BaseMapper<RagRetrievalTracePo> {
}
