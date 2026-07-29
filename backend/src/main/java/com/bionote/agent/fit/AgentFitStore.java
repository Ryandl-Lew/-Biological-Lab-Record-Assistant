package com.bionote.agent.fit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only persistence port for curve-fit record and tabular attachment projections. */
public interface AgentFitStore {
    List<Map<String,Object>> findCatalogRecords(UUID projectId, int limit);
    List<Map<String,Object>> findTabularAttachments(UUID recordId);
    List<Map<String,Object>> findRecords(UUID projectId, FitModels.FitIntent intent, int limit);
}
