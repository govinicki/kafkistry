package com.infobip.kafkistry.service.resources

import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.kafkastate.ClusterEnabledFilter
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.generator.balance.percentageOf
import com.infobip.kafkistry.service.generator.balance.percentageOfNullable
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springframework.stereotype.Service

@Service
class TopicResourcesAnalyzer(
    private val clusterResourcesAnalyzer: ClusterResourcesAnalyzer,
    private val topicsRegistryService: TopicsRegistryService,
    private val clustersRegistryService: ClustersRegistryService,
    private val enabledFilter: ClusterEnabledFilter,
    private val usageLevelClassifier: UsageLevelClassifier,
    private val topicDiskAnalyzer: TopicDiskAnalyzer,
) {

    fun topicOnClusterDiskUsage(
        topic: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
    ): TopicClusterDiskUsageExt {
        val cluster = clustersRegistryService.getCluster(clusterIdentifier)
        val topicDescription = topicsRegistryService.findTopic(topic)
        return analyzeTopicDiskUsage(topic, topicDescription, cluster.ref(), false)
    }

    fun topicDryRunDiskUsage(
        topicDescription: TopicDescription
    ): Map<KafkaClusterIdentifier, OptionalValue<TopicClusterDiskUsageExt>> {
        return clustersRegistryService.listClustersRefs()
            .filter { enabledFilter.enabled(it) }
            .filter { topicDescription.presence.needToBeOnCluster(it) }
            .associate { clusterRef ->
                val topicDiskUsage = try {
                    analyzeTopicDiskUsage(topicDescription.name, topicDescription, clusterRef, true)
                        .let { OptionalValue.of(it) }
                } catch (e: Exception) {
                    OptionalValue.absent(e.deepToString())
                }
                clusterRef.identifier to topicDiskUsage
            }
    }

    private fun analyzeTopicDiskUsage(
        topic: TopicName,
        topicDescription: TopicDescription?,
        clusterRef: ClusterRef,
        preferUsingDescriptionProps: Boolean,
    ): TopicClusterDiskUsageExt {
        val topicClusterDiskUsage = topicDiskAnalyzer.analyzeTopicDiskUsage(
            topic, topicDescription, clusterRef, preferUsingDescriptionProps
        )
        val clusterDiskUsage = clusterResourcesAnalyzer.clusterDiskUsage(clusterRef.identifier)
        val brokerUsages = topicClusterDiskUsage.brokerUsages.mapValues { (it, diskUsage) ->
            val brokerTotalUsedBytes = clusterDiskUsage.brokerUsages[it]?.usage?.totalUsedBytes
            val brokerPossibleUsedBytes = clusterDiskUsage.brokerUsages[it]?.usage?.boundedSizePossibleUsedBytes
            TopicDiskUsageExt(
                usage = diskUsage,
                retentionBoundedBrokerTotalBytes = diskUsage.retentionBoundedBytes
                        plusNullable -diskUsage.actualUsedBytes.orElse(0)
                        plusNullable brokerTotalUsedBytes,
                retentionBoundedBrokerPossibleBytes = diskUsage.retentionBoundedBytes
                        plusNullable -diskUsage.existingRetentionBoundedBytes.orElse(0)
                        plusNullable brokerPossibleUsedBytes,
            )
        }
        val combinedUsage = brokerUsages.values.reduce(TopicDiskUsageExt::plus)
        return TopicClusterDiskUsageExt(
            unboundedSizeRetention = topicClusterDiskUsage.unboundedSizeRetention,
            configuredReplicaRetentionBytes = topicClusterDiskUsage.configuredReplicaRetentionBytes,
            combined = combinedUsage,
            combinedPortions = combinedUsage.portionOf(clusterDiskUsage.combined.usage),
            brokerUsages = brokerUsages,
            brokerPortions = brokerUsages.mapValues { (broker, usage) ->
                usage.portionOf(clusterDiskUsage.brokerUsages[broker]?.usage ?: BrokerDiskUsage.ZERO)
            },
            clusterDiskUsage = clusterDiskUsage,
        )
    }

    private fun TopicDiskUsageExt.portionOf(brokerUsage: BrokerDiskUsage): UsagePortions {
        val retentionBoundedBrokerTotalBytesPercentOfCapacity =
            retentionBoundedBrokerTotalBytes percentageOfNullable brokerUsage.totalCapacityBytes
        val retentionBoundedBrokerPossibleBytesPercentOfCapacity =
            retentionBoundedBrokerPossibleBytes percentageOfNullable brokerUsage.totalCapacityBytes
        return with(usage) {
            UsagePortions(
                replicasPercent = replicasCount percentageOfNullable brokerUsage.replicasCount,
                orphanedReplicasPercent = orphanedReplicasCount percentageOf brokerUsage.orphanedReplicasCount,
                actualUsedBytesPercentOfBrokerTotal = actualUsedBytes percentageOfNullable brokerUsage.totalUsedBytes,
                actualUsedBytesPercentOfBrokerCapacity = actualUsedBytes percentageOfNullable brokerUsage.totalCapacityBytes,
                actualUsedBytesPercentOfExpected = actualUsedBytes percentageOfNullable expectedUsageBytes,
                retentionBoundedBytesPercentOfBrokerTotal = retentionBoundedBytes percentageOfNullable brokerUsage.totalUsedBytes?.takeIf { it > 0 },
                retentionBoundedBytesPercentOfBrokerCapacity = retentionBoundedBytes percentageOfNullable brokerUsage.totalCapacityBytes,
                retentionBoundedBytesPercentOfExpected = retentionBoundedBytes percentageOfNullable expectedUsageBytes,
                retentionBoundedBrokerTotalBytesPercentOfCapacity = retentionBoundedBrokerTotalBytesPercentOfCapacity,
                retentionBoundedBrokerPossibleBytesPercentOfCapacity = retentionBoundedBrokerPossibleBytesPercentOfCapacity,
                possibleClusterUsageLevel = usageLevelClassifier.determineLevel(retentionBoundedBrokerTotalBytesPercentOfCapacity),
                totalPossibleClusterUsageLevel = usageLevelClassifier.determineLevel(retentionBoundedBrokerPossibleBytesPercentOfCapacity),
            )
        }
    }

    private fun <T : Number> T?.orElse(default: T): T = this ?: default

}

