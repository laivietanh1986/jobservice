package com.example.jobservice.enums;

public enum JobStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED;
  public boolean canTransitionTo(JobStatus nextStatus) {
    return switch (this) {
      case PENDING -> nextStatus == PROCESSING;
      case PROCESSING -> nextStatus == COMPLETED || nextStatus == FAILED;
      default -> false;
    };
  }
}
