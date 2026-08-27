package com.example.jobservice.service.implement;

import com.example.jobservice.dto.CreateJobRequest;
import com.example.jobservice.dto.JobDto;
import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.model.JobEntity;
import com.example.jobservice.repository.JobRepository;
import com.example.jobservice.service.JobService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class JobServiceImplement implements JobService {
  private final JobRepository jobRepository;
  private final TransactionTemplate transactionTemplate;
  private static final int MAX_RETRIES = 3;

  public JobServiceImplement(JobRepository jobRepository, TransactionTemplate transactionTemplate) {
    this.jobRepository = jobRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public JobDto createJob(CreateJobRequest request) {
    JobEntity jobEntity =  new JobEntity();
    jobEntity.setType(request.getType());
    jobEntity.setPayload(request.getPayload());
    jobEntity.setStatus(JobStatus.PENDING);
    jobRepository.save(jobEntity);
    return mapToJobDto(jobEntity);

  }

  @Override
  public JobDto getJobById(long id) {
   JobEntity jobEntity = jobRepository.getReferenceById(id);
   return mapToJobDto(jobEntity);
  }

  @Override
  public List<JobDto> getJobs(JobStatus status, int page, int size) {
    Pageable pageable =  PageRequest.of(page,size);
    return jobRepository.findByStatus(status,pageable)
        .stream().map(this::mapToJobDto)
        .collect(Collectors.toList());

  }

  @Override
  public List<JobDto> process() {
    List<JobEntity> claimedJobs = transactionTemplate.execute(status -> {
      Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
      List<JobEntity> pendingJobs = jobRepository.findByStatusForUpdate(JobStatus.PENDING, pageable);
      pendingJobs.forEach(job -> job.setStatus(JobStatus.PROCESSING));
      return jobRepository.saveAllAndFlush(pendingJobs);
    });

    transactionTemplate.executeWithoutResult(status -> {
      claimedJobs.forEach(this::processJobWithRetries);
      jobRepository.saveAllAndFlush(claimedJobs);
    });

    return claimedJobs.stream().map(this::mapToJobDto).collect(Collectors.toList());
  }

  private void processJobWithRetries(JobEntity jobEntity) {

    while (jobEntity.getRetryCount() < MAX_RETRIES) {
      try {
        if (isSuccess(jobEntity)) {
          jobEntity.setStatus(JobStatus.COMPLETED);
          jobEntity.setErrorMessage(null);
          return;
        }
        jobEntity.setErrorMessage("Processing failed: payload marked fail=true");
      } catch (Exception exception) {
        jobEntity.setErrorMessage("Got exception when processing job: " + exception.getMessage());
      }
      jobEntity.setRetryCount(jobEntity.getRetryCount() + 1);
    }
    jobEntity.setStatus(JobStatus.FAILED);
  }

  private boolean isSuccess(JobEntity jobEntity) {
    Map<String, String> payload = jobEntity.getPayload();
    boolean shouldFail = payload != null && Boolean.parseBoolean(payload.get("fail"));
    return !shouldFail;
  }

  private JobDto mapToJobDto(JobEntity jobEntity) {
    return JobDto.builder()
        .id(jobEntity.getId())
        .type(jobEntity.getType())
        .payload(jobEntity.getPayload())
        .status(jobEntity.getStatus())
        .createdAt(jobEntity.getCreatedAt())
        .updatedAt(jobEntity.getUpdatedAt())
        .build();
  }
}
