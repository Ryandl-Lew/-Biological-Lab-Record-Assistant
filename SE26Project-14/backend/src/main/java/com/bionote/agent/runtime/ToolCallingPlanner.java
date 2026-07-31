package com.bionote.agent.runtime;

import com.bionote.agent.model.AgentModelResponse;
import com.bionote.agent.model.ModelToolCall;
import com.bionote.agent.tool.AgentToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class ToolCallingPlanner implements AgentPlanner {
    private final AgentToolRegistry tools;

    public ToolCallingPlanner(AgentToolRegistry tools) {
        this.tools = tools;
    }

    @Override
    public PlannerDecision next(AgentRunContext context, AgentModelResponse response) {
        if (response.errorCode() != null)
            return new PlannerDecision.Fail(response.errorCode(), response.errorMessage());
        boolean hasTools = !response.toolCalls().isEmpty(),
                hasFinal = response.finalOutput() != null;
        if (hasTools && hasFinal)
            return new PlannerDecision.Fail(
                    "AGENT_MODEL_RESPONSE_INVALID",
                    "Model returned tools and final output together");
        if (hasTools) {
            if (context.run().toolCallCount() + response.toolCalls().size()
                    > context.limits().maxToolCalls())
                return new PlannerDecision.Fail("AGENT_LIMIT_EXCEEDED", "Tool call limit exceeded");
            for (ModelToolCall call : response.toolCalls()) {
                if (call.name() == null || tools.find(call.name()).isEmpty())
                    return new PlannerDecision.Fail(
                            "AGENT_UNKNOWN_TOOL", "Unknown tool: " + call.name());
                if (call.arguments() == null || !call.arguments().isObject())
                    return new PlannerDecision.Fail(
                            "AGENT_TOOL_ARGUMENTS_INVALID", "Tool arguments must be a JSON object");
            }
            return new PlannerDecision.ExecuteTools(response.toolCalls());
        }
        if (hasFinal) return new PlannerDecision.Finish(response.finalOutput());
        return new PlannerDecision.Fail(
                "AGENT_MODEL_RESPONSE_INVALID", "Model returned neither tools nor final output");
    }
}
