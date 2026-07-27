package com.bionote.agent.runtime;

import com.bionote.agent.model.AgentModelResponse;

public interface AgentPlanner { PlannerDecision next(AgentRunContext context,AgentModelResponse response); }
