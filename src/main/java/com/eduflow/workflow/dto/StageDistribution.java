package com.eduflow.workflow.dto;

import java.util.UUID;

/**
 * Aggregate row: how many active students sit in a given stage (PRD §16 "Students by Stage").
 * Populated via a JPQL constructor expression.
 */
public record StageDistribution(UUID stageId, String stageName, String color, long count) {}
