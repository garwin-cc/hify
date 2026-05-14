package com.hify.auth.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.auth.domain.AuditLogPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogPo> {
}
