package com.example.jobservice.repository;

import com.example.jobservice.enums.JobStatus;
import com.example.jobservice.model.JobEntity;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends JpaRepository<JobEntity,Long> {
  Page<JobEntity> findByStatus(JobStatus status, Pageable pageable);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT j FROM JobEntity j WHERE j.status = :status")
  List<JobEntity> findByStatusForUpdate(@Param("status") JobStatus status, Pageable pageable);

}
