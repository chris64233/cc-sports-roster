package com.chris64233.cc.sportsroster.repo;

import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.cc.sportsroster.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByIdInOrderByCodeAsc(List<Long> ids);

    /**
     * 对涉及转会的球员行加悲观写锁，按 id 排序获取以避免多笔转会交叉加锁造成死锁，
     * 并保证并发争抢同一球员的转会被串行化。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id in :ids order by p.id asc")
    List<Player> findByIdInForUpdate(@Param("ids") List<Long> ids);
}
