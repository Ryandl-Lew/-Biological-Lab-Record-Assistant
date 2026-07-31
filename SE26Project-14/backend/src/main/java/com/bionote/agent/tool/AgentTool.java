package com.bionote.agent.tool;

public interface AgentTool<I, O> {
    AgentToolDefinition definition();

    Class<I> inputType();

    AgentToolResult<O> execute(AgentToolContext context, I input);
}
