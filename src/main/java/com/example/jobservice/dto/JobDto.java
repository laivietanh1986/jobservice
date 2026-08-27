package com.example.jobservice.dto;

import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.enums.JobType;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDto {
  private Long id;
  private JobType type;
  private Map<String,String> payload;
  private JobStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

}
