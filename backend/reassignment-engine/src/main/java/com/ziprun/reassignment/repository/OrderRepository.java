package com.ziprun.reassignment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ziprun.reassignment.entity.Order;
import com.ziprun.reassignment.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByAssignedAgent_Id(String agentId);

    List<Order> findByAssignedAgent_IdAndStatus(String agentId, OrderStatus status);
}
