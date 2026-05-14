package com.hify.model.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.model.domain.ModelDefaultPolicyPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelDefaultPolicyMapper extends BaseMapper<ModelDefaultPolicyPo> {
}
