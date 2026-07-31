package com.bionote.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelToolCall(String id, String name, JsonNode arguments) {}
