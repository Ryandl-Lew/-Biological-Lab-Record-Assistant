package com.bionote.agent.config;

/**
 * Shared LLM credentials loaded from the repository-local llm file.
 * Keys must never be logged or returned to clients.
 */
public record AgentCredentials(
        String provider,
        String model,
        String baseUrl,
        String apiKey
) {}
