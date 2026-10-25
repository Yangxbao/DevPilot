package com.devpilot.module.defect.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devpilot.module.defect.Defect;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DefectMapper extends BaseMapper<Defect> {
}
