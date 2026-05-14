package com.hify.auth.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.auth.domain.ProjectPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectPo> {
}
