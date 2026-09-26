package com.chris64233.cc.sportsroster.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.TransferItem;

public interface TransferItemRepository extends JpaRepository<TransferItem, Long> {

    @Query("select i from TransferItem i join fetch i.player p "
            + "where i.application.id = :applicationId order by i.id asc")
    List<TransferItem> findByApplicationIdWithPlayer(@Param("applicationId") Long applicationId);
}
