package com.bionote.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private boolean enabled;
    private String provider = "fake";
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "fake-deterministic-v1";
    private long timeoutMs = 45000;
    private long workerPollMs = 1000;
    private long workerStaleTimeoutMs = 600000;
    private int maxSteps = 120;
    private int maxToolCalls = 60;
    private int maxModelCalls = 24;
    private long maxDurationMs = 480000;
    private int maxOutputTokens = 6000;
    private int maxRepairTurns = 3;
    private int maxConcurrentPerUser = 4;
    private int maxConcurrentPerProject = 4;
    private long sameSubjectCooldownSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String value) {
        provider = value;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String value) {
        baseUrl = value;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String value) {
        apiKey = value;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String value) {
        model = value;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long value) {
        timeoutMs = value;
    }

    public long getWorkerPollMs() {
        return workerPollMs;
    }

    public void setWorkerPollMs(long value) {
        workerPollMs = value;
    }

    public long getWorkerStaleTimeoutMs() {
        return workerStaleTimeoutMs;
    }

    public void setWorkerStaleTimeoutMs(long value) {
        workerStaleTimeoutMs = value;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int value) {
        maxSteps = value;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int value) {
        maxToolCalls = value;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int value) {
        maxModelCalls = value;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(long value) {
        maxDurationMs = value;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int value) {
        maxOutputTokens = value;
    }

    public int getMaxRepairTurns() {
        return maxRepairTurns;
    }

    public void setMaxRepairTurns(int value) {
        maxRepairTurns = value;
    }

    public int getMaxConcurrentPerUser() {
        return maxConcurrentPerUser;
    }

    public void setMaxConcurrentPerUser(int value) {
        maxConcurrentPerUser = value;
    }

    public int getMaxConcurrentPerProject() {
        return maxConcurrentPerProject;
    }

    public void setMaxConcurrentPerProject(int value) {
        maxConcurrentPerProject = value;
    }

    public long getSameSubjectCooldownSeconds() {
        return sameSubjectCooldownSeconds;
    }

    public void setSameSubjectCooldownSeconds(long value) {
        sameSubjectCooldownSeconds = value;
    }
}
