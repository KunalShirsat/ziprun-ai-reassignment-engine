package com.ziprun.reassignment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ziprun.reassignment.entity.Agent;
import com.ziprun.reassignment.entity.AgentStatus;

public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findByStatus(AgentStatus status);
}
