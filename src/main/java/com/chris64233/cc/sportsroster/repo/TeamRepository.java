package com.chris64233.cc.sportsroster.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.cc.sportsroster.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
