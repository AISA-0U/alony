package com.mindskip.xzs.controller.admin;

import com.mindskip.xzs.base.BaseApiController;
import com.mindskip.xzs.base.RestResponse;
import com.mindskip.xzs.domain.JobPosition;
import com.mindskip.xzs.repository.JobPositionMapper;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/job-position")
public class JobPositionController extends BaseApiController {

    private final JobPositionMapper jobPositionMapper;

    public JobPositionController(JobPositionMapper jobPositionMapper) {
        this.jobPositionMapper = jobPositionMapper;
    }

    @PostMapping("/list")
    public RestResponse<List<JobPosition>> list() {
        return RestResponse.ok(jobPositionMapper.selectEnabled());
    }

    @PostMapping("/save")
    public RestResponse<JobPosition> save(@RequestBody @Valid JobPosition position) {
        if (position.getId() == null) {
            jobPositionMapper.insertSelective(position);
        } else {
            jobPositionMapper.updateByPrimaryKeySelective(position);
        }
        return RestResponse.ok(position);
    }
}
