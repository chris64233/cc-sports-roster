package com.chris64233.cc.sportsroster.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.chris64233.cc.sportsroster.domain.TransferApplication;
import com.chris64233.cc.sportsroster.repo.TransferApplicationRepository;

/**
 * 申请行单独在新事务中插入：申请号唯一约束冲突时只回滚内层事务，
 * 外层事务仍可读取胜出申请并按幂等重放返回。
 */
@Component
public class TransferApplicationCommands {

    private final TransferApplicationRepository applicationRepository;

    public TransferApplicationCommands(TransferApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferApplication insert(TransferApplication application)
            throws DataIntegrityViolationException {
        return applicationRepository.saveAndFlush(application);
    }
}
