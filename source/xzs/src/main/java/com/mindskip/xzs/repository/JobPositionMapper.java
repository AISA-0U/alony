package com.mindskip.xzs.repository;

import com.mindskip.xzs.domain.JobPosition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface JobPositionMapper extends BaseMapper<JobPosition> {
    List<JobPosition> selectEnabled();
}
