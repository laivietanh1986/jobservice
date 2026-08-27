package com.example.jobservice.model;

import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.enums.JobType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name="Jobs")
@Data
public class JobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;
  @NotNull(message = "Job type is required")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobType type;
  @ElementCollection
  @CollectionTable(name = "job_payload", joinColumns = @JoinColumn(name = "job_id"))
  @MapKeyColumn(name = "payload_key")
  @Column(name = "payload_value")
  private Map<String, String> payload;
  @NotNull(message = "Status is required")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private JobStatus status;
  private int retryCount = 0;
  private String errorMessage;
  @CreationTimestamp
  @Column(name = "created_at",nullable = false,updatable = false)
  private LocalDateTime createdAt;
  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;





}
