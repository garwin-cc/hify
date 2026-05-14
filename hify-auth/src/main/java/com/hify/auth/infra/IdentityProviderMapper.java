package com.hify.auth.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.auth.domain.IdentityProviderPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdentityProviderMapper extends BaseMapper<IdentityProviderPo> {
}
