package com.hify.knowledge.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.knowledge.domain.KnowledgeBasePo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBasePo> {
}
