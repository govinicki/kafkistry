<#-- @ftlvariable name="lastCommit"  type="java.lang.String" -->
<#-- @ftlvariable name="appUrl" type="com.infobip.kafkistry.webapp.url.AppUrl" -->
<#-- @ftlvariable name="consumersData" type="com.infobip.kafkistry.service.consumers.AllConsumersData" -->

<html lang="en">

<head>
    <#include "../commonResources.ftl"/>
    <title>Kafkistry: Consumer groups</title>
    <meta name="current-nav" content="nav-consumer-groups"/>
    <script src="static/consumer/consumerGroups.js?ver=${lastCommit}"></script>
</head>

<body>

<style>
    .tooltip-inner {
        max-width: 750px !important;
    }
</style>

<#include "../commonMenu.ftl">
<#import "../common/util.ftl" as util>
<#import "../common/infoIcon.ftl" as info>
<#import "../common/documentation.ftl" as doc>
<#import "util.ftl" as statusUtil>

<div class="container">
    <div class="card">
        <div class="card-header">
            <span class="h5">Cluster pooling statuses</span>
        </div>
        <div class="card-body p-1">
            <#list consumersData.clustersDataStatuses as clusterDataStatus>
                <#assign stateClass = util.levelToHtmlClass(clusterDataStatus.clusterStatus.level)>
                <#assign clusterTooltip>
                    ${clusterDataStatus.clusterStatus}<br/>
                    Last refresh before: ${(.now?long - clusterDataStatus.lastRefreshTime)/1000} sec
                </#assign>
                <div class="alert alert-sm alert-inline ${stateClass} cluster-status-badge status-filter-btn" role="alert"
                     data-status-type="${clusterDataStatus.clusterIdentifier}"
                     title="Click to filter by..." data-table-id="consumer-groups">
                    ${clusterDataStatus.clusterIdentifier} <@info.icon tooltip=clusterTooltip/>
                </div>
            </#list>
        </div>
    </div>
    <div class="card">
        <div class="card-header">
            <span class="h5">Statistics/counts</span>
        </div>
        <div class="card-body p-1">
            <#assign consumersStats = consumersData.consumersStats>
            <#include "../consumers/consumerGroupsCounts.ftl">
        </div>
    </div>
    <div class="card">
        <div class="card-header">
            <span class="h4">All clusters consumers</span>
        </div>
        <div class="card-body pl-0 pr-0">
            <table id="consumer-groups" class="table table-bordered datatable display">
                <thead class="thead-dark">
                <tr>
                    <th>Group</th>
                    <th>Status</th>
                    <th>Assignor</th>
                    <th>Lag</th>
                    <th>Topics</th>
                </tr>
                </thead>
                <#list consumersData.clustersGroups as clusterGroup>
                    <#assign consumerGroup = clusterGroup.consumerGroup>
                    <tr>
                        <td>
                            <a href="${appUrl.consumerGroups().showConsumerGroup(clusterGroup.clusterIdentifier, consumerGroup.groupId)}"
                               class="btn btn-sm btn-outline-dark mb-1">
                                ${consumerGroup.groupId} @ ${clusterGroup.clusterIdentifier} 🔍
                            </a>
                        </td>
                        <td>
                            <@util.namedTypeStatusAlert type=consumerGroup.status/>
                        </td>
                        <td>
                            ${consumerGroup.partitionAssignor}
                        </td>
                        <td>
                            <@util.namedTypeStatusAlert type=consumerGroup.lag.status/>
                        </td>
                        <td>
                            <span class="text-nowrap">
                                ${consumerGroup.topicMembers?size} topic(s)
                                <#assign topicsTooltip>
                                    <table class='table table-sm'>
                                        <thead class='thead-light'>
                                        <tr>
                                            <th><strong>Lag</strong></th>
                                            <th class='text-center'><strong>Topic</strong></th>
                                        </tr>
                                        </thead>
                                        <#list consumerGroup.topicMembers as topicMember>
                                            <tr class='top-bordered'>
                                                <td>
                                                    <#if (topicMember.lag.amount)??>
                                                        ${util.prettyNumber(topicMember.lag.amount)}
                                                    <#else>
                                                        N/A
                                                    </#if>
                                                <td>${topicMember.topicName}</td>
                                            </tr>
                                        </#list>
                                    </table>
                                </#assign>
                                <@info.icon tooltip=topicsTooltip/>
                            </span>
                        </td>
                    </tr>
                </#list>
            </table>
        </div>
    </div>
</div>

<#include "../common/pageBottom.ftl">
</body>
</html>