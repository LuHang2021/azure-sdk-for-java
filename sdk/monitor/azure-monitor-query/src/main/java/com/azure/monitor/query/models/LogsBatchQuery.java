// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.monitor.query.models;

import com.azure.core.annotation.Fluent;
import com.azure.core.util.CoreUtils;
import com.azure.monitor.query.log.implementation.models.LogsQueryHelper;
import com.azure.monitor.query.log.implementation.models.BatchQueryRequest;
import com.azure.monitor.query.log.implementation.models.QueryBody;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.azure.monitor.query.log.implementation.models.LogsQueryHelper.buildPreferHeaderString;

/**
 * A fluent class to create a batch of logs queries.
 */
@Fluent
public final class LogsBatchQuery {
    private final List<BatchQueryRequest> queries = new ArrayList<>();
    private int index;
    private Duration maxServerTimeout;

    static {
        LogsQueryHelper.setAccessor(new LogsQueryHelper.BatchQueryAccessor() {
            @Override
            public List<BatchQueryRequest> getBatchQueries(LogsBatchQuery query) {
                return query.getBatchQueries();
            }

            @Override
            public Duration getMaxServerTimeout(LogsBatchQuery query) {
                return query.getMaxServerTimeout();
            }
        });
    }

    /**
     * Adds a new logs query to the batch.
     * @param workspaceId The workspaceId on which the query is executed.
     * @param query The Kusto query.
     * @param timeSpan The time period for which the logs should be queried.
     * @return The updated {@link LogsBatchQuery}.
     */
    public LogsBatchQuery addQuery(String workspaceId, String query, QueryTimeSpan timeSpan) {
        return addQuery(workspaceId, query, timeSpan, new LogsQueryOptions());
    }

    /**
     * Adds a new logs query to the batch.
     * @param workspaceId The workspaceId on which the query is executed.
     * @param query The Kusto query.
     * @param timeSpan The time period for which the logs should be queried.
     * @param logsQueryOptions The log query options to configure server timeout, set additional workspaces or enable
     * statistics and rendering information in response.
     * @return The updated {@link LogsBatchQuery}.
     */
    public LogsBatchQuery addQuery(String workspaceId, String query, QueryTimeSpan timeSpan,
                                   LogsQueryOptions logsQueryOptions) {
        Objects.requireNonNull(query, "'query' cannot be null.");
        Objects.requireNonNull(workspaceId, "'workspaceId' cannot be null.");
        index++;
        QueryBody queryBody = new QueryBody(query)
                .setWorkspaces(logsQueryOptions == null ? null : logsQueryOptions.getAdditionalWorkspaces())
                .setTimespan(timeSpan == null ? null : timeSpan.toString());

        String preferHeader = buildPreferHeaderString(logsQueryOptions);
        if (logsQueryOptions != null && logsQueryOptions.getServerTimeout() != null) {
            if (logsQueryOptions.getServerTimeout().compareTo(this.maxServerTimeout) > 0) {
                maxServerTimeout = logsQueryOptions.getServerTimeout();
            }
        }
        Map<String, String> headers = new HashMap<>();
        if (!CoreUtils.isNullOrEmpty(preferHeader)) {
            headers.put("Prefer", preferHeader);
        }
        BatchQueryRequest batchQueryRequest = new BatchQueryRequest(String.valueOf(index), queryBody, workspaceId)
                .setHeaders(headers)
                .setPath("/query")
                .setMethod("POST");

        queries.add(batchQueryRequest);
        return this;
    }

    List<BatchQueryRequest> getBatchQueries() {
        return this.queries;
    }

    Duration getMaxServerTimeout() {
        return this.maxServerTimeout;
    }
}
