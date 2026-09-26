package com.chris64233.cc.sportsroster.repo;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.TransferApplication;

public interface TransferApplicationRepository extends JpaRepository<TransferApplication, Long> {

    Optional<TransferApplication> findByApplicationNo(String applicationNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferApplication t where t.id = :id")
    Optional<TransferApplication> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferApplication t where t.applicationNo = :applicationNo")
    Optional<TransferApplication> findByApplicationNoForUpdate(@Param("applicationNo") String applicationNo);
}
