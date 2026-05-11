package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Scheduling constraints that govern which nodes can host a given instance.
 *
 * These constraints are evaluated by the cluster scheduler when placing or
 * migrating instances. The client UI presents these as configurable rules
 * before instance creation or migration.
 */
@Serializable
data class SchedulingConstraints(
    /** Minimum node score required to host this instance. */
    @SerialName("min_node_score") val minNodeScore: Double = 0.0,

    /** List of required node labels (ALL must match). */
    @SerialName("required_labels") val requiredLabels: Map<String, String> = emptyMap(),

    /** List of forbidden node labels (NONE must match). */
    @SerialName("forbidden_labels") val forbiddenLabels: Map<String, String> = emptyMap(),

    /** Preferred node peer IDs (ordered by preference). */
    @SerialName("preferred_hosts") val preferredHosts: List<String> = emptyList(),

    /** Blacklisted node peer IDs (must NOT be placed on these). */
    @SerialName("blacklisted_hosts") val blacklistedHosts: List<String> = emptyList(),

    /** Maximum number of instances this type can co-locate on the same node. */
    @SerialName("max_co_located") val maxCoLocated: Int = 0, // 0 = no limit

    /** Whether this instance requires a dedicated node (no co-location). */
    @SerialName("dedicated_host") val dedicatedHost: Boolean = false,

    /** CPU architecture requirement (e.g. "amd64", "arm64"). */
    val architecture: String? = null,

    /** Minimum CPU cores available on target node. */
    @SerialName("min_cpu_cores") val minCpuCores: Int? = null,

    /** Minimum memory (GB) available on target node. */
    @SerialName("min_memory_gb") val minMemoryGb: Int? = null,

    /** Minimum free disk (GB) on target node. */
    @SerialName("min_disk_gb") val minDiskGb: Int? = null,

    /** Geographic region constraint (e.g. "us-east", "eu-west"). */
    val region: String? = null,

    /** Zone constraint within region (e.g. "us-east-1a"). */
    val zone: String? = null,

    /** Anti-affinity key: instances sharing the same key value must not co-locate. */
    @SerialName("anti_affinity_key") val antiAffinityKey: String? = null,

    /** Affinity key: instances sharing the same key value should preferably co-locate. */
    @SerialName("affinity_key") val affinityKey: String? = null,

    /** Priority class: "critical", "high", "normal", "low", "best-effort". */
    @SerialName("priority_class") val priorityClass: String = "normal",

    /** Whether this instance can be preempted by higher-priority instances. */
    val preemptible: Boolean = false
)

/**
 * UI-friendly preset for common scheduling constraint combinations.
 */
enum class SchedulingPreset(
    val label: String,
    val description: String
) {
    HIGH_AVAILABILITY(
        label = "高可用",
        description = "分散部署到不同区域，禁止与同类实例共置"
    ),
    PERFORMANCE(
        label = "性能优先",
        description = "部署到高分配置节点，允许独占宿主机"
    ),
    BALANCED(
        label = "均衡",
        description = "默认调度策略，无特殊约束"
    ),
    ISOLATED(
        label = "隔离",
        description = "需要独占宿主机，指定区域"
    ),
    LOW_PRIORITY(
        label = "低优先级",
        description = "可被抢占，部署到任意满足条件的节点"
    );

    /**
     * Convert a preset to a base [SchedulingConstraints] configuration.
     */
    fun toConstraints(): SchedulingConstraints = when (this) {
        HIGH_AVAILABILITY -> SchedulingConstraints(
            minNodeScore = 80.0,
            maxCoLocated = 0,
            dedicatedHost = true,
            antiAffinityKey = "instance-type",
            priorityClass = "high",
            preemptible = false
        )
        PERFORMANCE -> SchedulingConstraints(
            minNodeScore = 90.0,
            minCpuCores = 4,
            minMemoryGb = 8,
            dedicatedHost = true,
            priorityClass = "high",
            preemptible = false
        )
        BALANCED -> SchedulingConstraints(
            minNodeScore = 50.0,
            priorityClass = "normal"
        )
        ISOLATED -> SchedulingConstraints(
            minNodeScore = 70.0,
            dedicatedHost = true,
            priorityClass = "critical",
            preemptible = false
        )
        LOW_PRIORITY -> SchedulingConstraints(
            minNodeScore = 0.0,
            priorityClass = "best-effort",
            preemptible = true
        )
    }
}

/**
 * Request body for applying scheduling constraints to an existing instance.
 */
@Serializable
data class ApplySchedulingConstraintsRequest(
    @SerialName("instance_id") val instanceId: String,
    val constraints: SchedulingConstraints,
    val reason: String // Required reason for audit trail
)

/**
 * Response from the scheduler after applying constraints.
 */
@Serializable
data class SchedulingConstraintsResponse(
    @SerialName("instance_id") val instanceId: String,
    val constraints: SchedulingConstraints,
    @SerialName("applied_at") val appliedAt: String,
    @SerialName("previous_constraints") val previousConstraints: SchedulingConstraints? = null,
    val warnings: List<String> = emptyList()
)

/**
 * Scheduler simulation result: what would happen if constraints were applied now.
 */
@Serializable
data class SchedulingSimulation(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("current_host") val currentHost: String,
    @SerialName("would_migrate") val wouldMigrate: Boolean,
    @SerialName("target_host") val targetHost: String? = null,
    @SerialName("eligible_hosts") val eligibleHosts: List<String> = emptyList(),
    @SerialName("constraint_violations") val constraintViolations: List<String> = emptyList()
)
