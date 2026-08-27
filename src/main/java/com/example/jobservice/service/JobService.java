package com.example.jobservice.service;

import com.example.jobservice.dto.CreateJobRequest;
import com.example.jobservice.dto.JobDto;
import com.example.jobservice.enums.JobStatus;
import java.util.List;

public interface JobService {

  JobDto createJob(CreateJobRequest request);

  JobDto getJobById(long id);

  List<JobDto> getJobs(JobStatus status, int page, int size);

  List<JobDto> process();
}
