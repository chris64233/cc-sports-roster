package com.chris64233.cc.sportsroster.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.sportsroster.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByIdInOrderByCodeAsc(List<Long> ids);
}
