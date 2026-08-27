package com.example.jobservice.controller;

import com.example.jobservice.dto.CreateJobRequest;
import com.example.jobservice.dto.JobDto;
import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {
  private final JobService jobService;
  @PostMapping
  public ResponseEntity<JobDto> createJob(@Valid @RequestBody CreateJobRequest request){
    JobDto jobDto = jobService.createJob(request);
    return new ResponseEntity<>(jobDto, HttpStatus.CREATED);
  }
  @GetMapping("/{id}")
  public ResponseEntity<JobDto> getJobById(@PathVariable long id){
    JobDto jobDto= jobService.getJobById(id);
    return ResponseEntity.ok(jobDto);
  }
  @GetMapping
  public ResponseEntity<Page<JobDto>> getJobs(@RequestParam JobStatus status,
      @RequestParam int page,
      @RequestParam int size){
    Page<JobDto> jobs = jobService.getJobs(status,page,size);
    return ResponseEntity.ok(jobs);
  }
  @PostMapping("/process")
  public ResponseEntity<List<JobDto>> processJobs(){
    List<JobDto> jobDtos= jobService.process();
    return ResponseEntity.ok(jobDtos);

  }


}
