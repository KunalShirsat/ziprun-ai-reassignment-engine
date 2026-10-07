package com.ziprun.reassignment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ziprun.reassignment.dto.CreateOrderRequest;
import com.ziprun.reassignment.dto.OrderResponse;
import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.entity.OrderStatus;
import com.ziprun.reassignment.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    public List<OrderResponse> getOrders(@RequestParam(required = false) OrderStatus status) {
        return orderService.getOrders(status);
    }

    @PostMapping("/{id}/suggest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SuggestionResponse generateSuggestion(@PathVariable String id) {
        return orderService.generateSuggestion(id);
    }
}
