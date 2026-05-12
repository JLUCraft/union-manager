package com.jlucraft.console.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


 *
@Serializable
data class SchedulingConstraints(

    @SerialName("min_node_score") val minNodeScore: Double = 0.0,


    @SerialName("required_labels") val requiredLabels: Map<String, String> = emptyMap(),


    @SerialName("forbidden_labels") val forbiddenLabels: Map<String, String> = emptyMap(),


    @SerialName("preferred_hosts") val preferredHosts: List<String> = emptyList(),


    @SerialName("blacklisted_hosts") val blacklistedHosts: List<String> = emptyList(),


    @SerialName("max_co_located") val maxCoLocated: Int = 0,


    @SerialName("dedicated_host") val dedicatedHost: Boolean = false,


    val architecture: String? = null,


    @SerialName("min_cpu_cores") val minCpuCores: Int? = null,


    @SerialName("min_memory_gb") val minMemoryGb: Int? = null,


    @SerialName("min_disk_gb") val minDiskGb: Int? = null,


    val region: String? = null,


    val zone: String? = null,


    @SerialName("anti_affinity_key") val antiAffinityKey: String? = null,


    @SerialName("affinity_key") val affinityKey: String? = null,


    @SerialName("priority_class") val priorityClass: String = "normal",


    val preemptible: Boolean = false
)


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


@Serializable
data class ApplySchedulingConstraintsRequest(
    @SerialName("instance_id") val instanceId: String,
    val constraints: SchedulingConstraints,
    val reason: String
)


@Serializable
data class SchedulingConstraintsResponse(
    @SerialName("instance_id") val instanceId: String,
    val constraints: SchedulingConstraints,
    @SerialName("applied_at") val appliedAt: String,
    @SerialName("previous_constraints") val previousConstraints: SchedulingConstraints? = null,
    val warnings: List<String> = emptyList()
)


@Serializable
data class SchedulingSimulation(
    @SerialName("instance_id") val instanceId: String,
    @SerialName("current_host") val currentHost: String,
    @SerialName("would_migrate") val wouldMigrate: Boolean,
    @SerialName("target_host") val targetHost: String? = null,
    @SerialName("eligible_hosts") val eligibleHosts: List<String> = emptyList(),
    @SerialName("constraint_violations") val constraintViolations: List<String> = emptyList()
)
