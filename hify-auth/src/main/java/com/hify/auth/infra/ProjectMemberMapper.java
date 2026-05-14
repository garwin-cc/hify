package com.hify.auth.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.auth.domain.ProjectMemberPo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMemberPo> {
}
