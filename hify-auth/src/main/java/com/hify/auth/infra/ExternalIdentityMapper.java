package com.hify.auth.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.auth.domain.ExternalIdentityPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentityPo> {
}
