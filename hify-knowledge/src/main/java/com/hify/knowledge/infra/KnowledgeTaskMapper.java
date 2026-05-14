package com.hify.knowledge.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.knowledge.domain.KnowledgeTaskPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeTaskMapper extends BaseMapper<KnowledgeTaskPo> {
}
