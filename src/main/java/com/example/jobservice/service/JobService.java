package com.example.jobservice.service;

import com.example.jobservice.dto.CreateJobRequest;
import com.example.jobservice.dto.JobDto;
import com.example.jobservice.enums.JobStatus;
import java.util.List;
import org.springframework.data.domain.Page;

public interface JobService {

  JobDto createJob(CreateJobRequest request);

  JobDto getJobById(long id);

  Page<JobDto> getJobs(JobStatus status, int page, int size);

  List<JobDto> process();
}
