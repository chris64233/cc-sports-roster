package com.chris64233.cc.sportsroster.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.sportsroster.domain.TransferApplication;
import com.chris64233.cc.sportsroster.repo.TransferApplicationRepository;

/**
 * 申请号唯一约束冲突后，外层事务仍需读取胜出申请以按幂等重放返回，
 * 该读取放在独立事务中，避免受内层插入失败影响。
 */
@Component
public class TransferIdempotencyQueries {

    private final TransferApplicationRepository applicationRepository;

    public TransferIdempotencyQueries(TransferApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<TransferApplication> findApplication(String applicationNo) {
        return applicationRepository.findByApplicationNo(applicationNo);
    }
}
