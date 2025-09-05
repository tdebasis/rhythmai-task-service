package com.rhythmai.task.service

import org.springframework.stereotype.Service

/**
 * Hybrid analytics service for privacy-first event tracking.
 * 
 * Phase 1: Events are formatted for Plausible Analytics (frontend integration)
 * Phase 2: Enhanced with MongoDB for business intelligence
 * 
 * No PII is tracked - only aggregated usage patterns.
 */
interface AnalyticsService {
    /**
     * Track a task-related event for product insights.
     * 
     * @param event Event name (e.g., "task_created", "task_completed") 
     * @param properties Privacy-safe event properties (no PII)
     */
    fun trackTaskEvent(event: String, properties: Map<String, Any>)
    
    /**
     * Track a user workflow event for engagement insights.
     * 
     * @param event Event name (e.g., "search_performed", "filter_applied")
     * @param properties Privacy-safe event properties
     */
    fun trackWorkflowEvent(event: String, properties: Map<String, Any>)
    
    /**
     * Track user journey milestones for retention analysis.
     * 
     * @param userId User identifier (hashed for privacy)
     * @param milestone Milestone name (e.g., "first_task", "fifth_task", "became_sticky")
     * @param context Additional context for business intelligence
     */
    fun trackUserJourneyMilestone(userId: String, milestone: String, context: Map<String, Any> = emptyMap())
    
    /**
     * Track feature usage for feature-retention correlation.
     * 
     * @param userId User identifier (hashed for privacy)
     * @param feature Feature name (e.g., "due_dates", "tags", "projects")
     * @param context Feature usage context
     */
    fun trackFeatureUsage(userId: String, feature: String, context: Map<String, Any> = emptyMap())
}

/**
 * Development implementation that logs events in Plausible-compatible format.
 * Production will queue events for frontend delivery to Plausible Analytics.
 */
@Service
class PlausibleReadyAnalyticsService : AnalyticsService {
    
    private fun hashUserId(userId: String): String {
        // Simple hash for privacy - production would use proper cryptographic hash
        return "user_${userId.hashCode().toString().takeLast(6)}"
    }
    
    override fun trackTaskEvent(event: String, properties: Map<String, Any>) {
        // Plausible custom event format
        println("📊 Plausible Event: $event")
        println("   Props: $properties")
        // Production: eventQueue.add(PlausibleEvent(event, properties))
    }
    
    override fun trackWorkflowEvent(event: String, properties: Map<String, Any>) {
        // Plausible custom event format  
        println("📊 Plausible Event: $event")
        println("   Props: $properties")
        // Production: eventQueue.add(PlausibleEvent(event, properties))
    }
    
    override fun trackUserJourneyMilestone(userId: String, milestone: String, context: Map<String, Any>) {
        val hashedUserId = hashUserId(userId)
        val eventProps = mapOf(
            "milestone" to milestone,
            "user_hash" to hashedUserId
        ) + context
        
        println("📊 Plausible Event: user_milestone")
        println("   Props: $eventProps")
        // Production: eventQueue.add(PlausibleEvent("user_milestone", eventProps))
        
        // Future: Also store in MongoDB for business intelligence
        // userJourneyRepository.save(UserJourneyEvent(hashedUserId, milestone, context))
    }
    
    override fun trackFeatureUsage(userId: String, feature: String, context: Map<String, Any>) {
        val hashedUserId = hashUserId(userId)
        val eventProps = mapOf(
            "feature" to feature,
            "user_hash" to hashedUserId
        ) + context
        
        println("📊 Plausible Event: feature_usage")
        println("   Props: $eventProps")
        // Production: eventQueue.add(PlausibleEvent("feature_usage", eventProps))
        
        // Future: Also analyze for feature-retention correlation in MongoDB
    }
}