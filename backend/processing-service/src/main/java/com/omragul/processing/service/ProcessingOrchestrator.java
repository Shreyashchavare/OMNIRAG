package com.omragul.processing.service;

import java.util.UUID;

public interface ProcessingOrchestrator {
    void submitJob(UUID jobId);
}
