package com.example.jobservice.service.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.jobservice.dto.CreateJobRequest;
import com.example.jobservice.dto.JobDto;
import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.enums.JobType;
import com.example.jobservice.exception.ResourceNotFoundException;
import com.example.jobservice.model.JobEntity;
import com.example.jobservice.repository.JobRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JobServiceImplementTest {

  @Mock
  private JobRepository jobRepository;

  @Mock
  private TransactionTemplate transactionTemplate;

  private JobServiceImplement jobService;

  @BeforeEach
  void setUp() {
    jobService = new JobServiceImplement(jobRepository, transactionTemplate);
  }

  @Test
  void createJob_successfully() {
    Map<String, String> payload = Map.of("key", "value");
    CreateJobRequest request = CreateJobRequest.builder()
        .type(JobType.EMAIL)
        .payload(payload)
        .build();

    when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    JobDto result = jobService.createJob(request);

    ArgumentCaptor<JobEntity> jobEntityCaptor = ArgumentCaptor.forClass(JobEntity.class);
    verify(jobRepository).save(jobEntityCaptor.capture());

    JobEntity savedEntity = jobEntityCaptor.getValue();
    assertThat(savedEntity.getType()).isEqualTo(JobType.EMAIL);
    assertThat(savedEntity.getPayload()).isEqualTo(payload);
    assertThat(savedEntity.getStatus()).isEqualTo(JobStatus.PENDING);

    assertThat(result).isNotNull();
    assertThat(result.getType()).isEqualTo(JobType.EMAIL);
    assertThat(result.getPayload()).isEqualTo(payload);
    assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
  }

  @Test
  void getJobById_returnsMatchingJobDto() {
    JobEntity entity = buildEntity(1L, JobType.CHECK, JobStatus.COMPLETED, Map.of("k", "v"));
    when(jobRepository.findById(1L)).thenReturn(Optional.of(entity));

    JobDto result = jobService.getJobById(1L);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getType()).isEqualTo(JobType.CHECK);
    assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(result.getPayload()).isEqualTo(Map.of("k", "v"));
  }

  @Test
  void getJobById_throwsProperError_whenJobDoesNotExist() {
    long missingId = 404L;
    when(jobRepository.findById(missingId))
        .thenThrow(new ResourceNotFoundException("Unable to find JobEntity with id " + missingId));

    assertThatThrownBy(() -> jobService.getJobById(missingId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(String.valueOf(missingId));
  }

  @Test
  void getJobs_returnsMappedDtos_forGivenStatusAndPage() {
    JobEntity entity = buildEntity(1L, JobType.CHECK, JobStatus.PENDING, Map.of());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    when(jobRepository.findByStatus(eq(JobStatus.PENDING), pageableCaptor.capture()))
        .thenReturn(List.of(entity));

    List<JobDto> result = jobService.getJobs(JobStatus.PENDING, 1, 5);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(1L);
    assertThat(result.get(0).getStatus()).isEqualTo(JobStatus.PENDING);

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(1);
    assertThat(pageable.getPageSize()).isEqualTo(5);
  }

  @Test
  void process_marksPendingJobAsCompleted_whenProcessingSucceeds() {
    JobEntity job = buildEntity(1L, JobType.EMAIL, JobStatus.PENDING, Map.of());
    stubTransactionTemplateToRunCallbacks();
    when(jobRepository.findByStatusForUpdate(eq(JobStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(job));
    when(jobRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    List<JobDto> result = jobService.process();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(job.getErrorMessage()).isNull();
    assertThat(job.getRetryCount()).isZero();
  }

  @Test
  void process_retriesJob_untilItEventuallySucceeds() {
    // A payload whose "fail" flag flips after two reads simulates a job that
    // fails on its first two attempts and succeeds on the third retry.
    Map<String, String> flakyPayload = new HashMap<>() {
      private int reads = 0;

      @Override
      public String get(Object key) {
        reads++;
        return reads < 3 ? "true" : "false";
      }
    };
    JobEntity job = buildEntity(1L, JobType.EMAIL, JobStatus.PENDING, flakyPayload);
    stubTransactionTemplateToRunCallbacks();
    when(jobRepository.findByStatusForUpdate(eq(JobStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(job));
    when(jobRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    List<JobDto> result = jobService.process();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(job.getRetryCount()).isEqualTo(2);
    assertThat(job.getErrorMessage()).isNull();
  }

  @Test
  void process_marksJobAsFailed_afterMaxRetryCountReached() {
    JobEntity job = buildEntity(1L, JobType.EMAIL, JobStatus.PENDING, Map.of("fail", "true"));
    stubTransactionTemplateToRunCallbacks();
    when(jobRepository.findByStatusForUpdate(eq(JobStatus.PENDING), any(Pageable.class)))
        .thenReturn(List.of(job));
    when(jobRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    List<JobDto> result = jobService.process();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(job.getRetryCount()).isEqualTo(3);
    assertThat(job.getErrorMessage()).isEqualTo("Processing failed: payload marked fail=true");
  }

  private JobEntity buildEntity(long id, JobType type, JobStatus status, Map<String, String> payload) {
    JobEntity entity = new JobEntity();
    entity.setId(id);
    entity.setType(type);
    entity.setStatus(status);
    entity.setPayload(payload);
    return entity;
  }

  @SuppressWarnings("unchecked")
  private void stubTransactionTemplateToRunCallbacks() {
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      TransactionCallback<Object> callback = invocation.getArgument(0);
      return callback.doInTransaction(mock(TransactionStatus.class));
    });
    doAnswer(invocation -> {
      Consumer<TransactionStatus> callback = invocation.getArgument(0);
      callback.accept(mock(TransactionStatus.class));
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());
  }
}
