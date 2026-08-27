package com.example.jobservice.dto;

import com.example.jobservice.enums.JobType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobRequest {
  @NotNull(message = "Job type is required")
  private JobType type;
  private Map<String,String>payload;


}
