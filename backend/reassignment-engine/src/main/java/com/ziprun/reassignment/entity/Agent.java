package com.ziprun.reassignment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * Entity class representing an Agent in the system.
 * An Agent is a delivery person who can be assigned orders.
 */
@Entity
public class Agent {

    /**
     * Unique identifier for the agent
     */
    @Id
    private String id;

    /**
     * Name of the agent
     */
    private String name;

    /**
     * Number of active orders currently assigned to the agent
     */
    private int activeOrderCount;

    /**
     * Current status of the agent (AVAILABLE, BUSY, OFFLINE)
     */
    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    /**
     * Default constructor required by JPA
     */
    public Agent() {
    }

    /**
     * Get the unique identifier of the agent
     * @return the agent's ID
     */
    public String getId() {
        return id;
    }

    /**
     * Set the unique identifier of the agent
     * @param id the agent's ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Get the name of the agent
     * @return the agent's name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the agent
     * @param name the agent's name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the number of active orders assigned to the agent
     * @return the count of active orders
     */
    public int getActiveOrderCount() {
        return activeOrderCount;
    }

    /**
     * Set the number of active orders assigned to the agent
     * @param activeOrderCount the count of active orders
     */
    public void setActiveOrderCount(int activeOrderCount) {
        this.activeOrderCount = activeOrderCount;
    }

    /**
     * Get the current status of the agent
     * @return the agent's status
     */
    public AgentStatus getStatus() {
        return status;
    }

    /**
     * Set the current status of the agent
     * @param status the agent's status
     */
    public void setStatus(AgentStatus status) {
        this.status = status;
    }
}
