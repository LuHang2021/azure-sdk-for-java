// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.monitor.query;

import com.azure.core.annotation.ReturnType;
import com.azure.core.annotation.ServiceClient;
import com.azure.core.annotation.ServiceMethod;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
import com.azure.core.util.CoreUtils;
import com.azure.monitor.query.log.implementation.AzureLogAnalyticsImpl;
import com.azure.monitor.query.log.implementation.models.BatchQueryRequest;
import com.azure.monitor.query.log.implementation.models.BatchQueryResponse;
import com.azure.monitor.query.log.implementation.models.BatchQueryResults;
import com.azure.monitor.query.log.implementation.models.BatchRequest;
import com.azure.monitor.query.log.implementation.models.BatchResponse;
import com.azure.monitor.query.log.implementation.models.ErrorInfo;
import com.azure.monitor.query.log.implementation.models.ErrorResponseException;
import com.azure.monitor.query.log.implementation.models.LogsQueryHelper;
import com.azure.monitor.query.log.implementation.models.QueryBody;
import com.azure.monitor.query.log.implementation.models.QueryResults;
import com.azure.monitor.query.log.implementation.models.Table;
import com.azure.monitor.query.models.LogsBatchQuery;
import com.azure.monitor.query.models.LogsBatchQueryResult;
import com.azure.monitor.query.models.LogsBatchQueryResultCollection;
import com.azure.monitor.query.models.LogsQueryError;
import com.azure.monitor.query.models.LogsQueryErrorDetail;
import com.azure.monitor.query.models.LogsQueryException;
import com.azure.monitor.query.models.LogsQueryOptions;
import com.azure.monitor.query.models.LogsQueryResult;
import com.azure.monitor.query.models.LogsQueryStatistics;
import com.azure.monitor.query.models.LogsQueryVisualization;
import com.azure.monitor.query.models.LogsTable;
import com.azure.monitor.query.models.LogsTableCell;
import com.azure.monitor.query.models.LogsTableColumn;
import com.azure.monitor.query.models.LogsTableRow;
import com.azure.monitor.query.models.QueryTimeSpan;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.azure.core.util.FluxUtil.withContext;

/**
 * The asynchronous client for querying Azure Monitor logs.
 */
@ServiceClient(builder = LogsQueryClientBuilder.class, isAsync = true)
public final class LogsQueryAsyncClient {

    private static final String AZURE_RESPONSE_TIMEOUT = "azure-response-timeout";
    public static final int CLIENT_TIMEOUT_BUFFER = 5;
    private final AzureLogAnalyticsImpl innerClient;

    /**
     * Constructor that has the inner generated client to make the service call.
     * @param innerClient The inner generated client.
     */
    LogsQueryAsyncClient(AzureLogAnalyticsImpl innerClient) {
        this.innerClient = innerClient;
    }

    /**
     * Returns all the Azure Monitor logs matching the given query in the specified workspaceId.
     * @param workspaceId The workspaceId where the query should be executed.
     * @param query The Kusto query to fetch the logs.
     * @param timeSpan The time period for which the logs should be looked up.
     * @return The logs matching the query.
     */
    @ServiceMethod(returns = ReturnType.SINGLE)
    public Mono<LogsQueryResult> queryLogs(String workspaceId, String query, QueryTimeSpan timeSpan) {
        return queryLogsWithResponse(workspaceId, query, timeSpan, new LogsQueryOptions())
                .map(Response::getValue);
    }

    /**
     * Returns all the Azure Monitor logs matching the given query in the specified workspaceId.
     * @param workspaceId The workspaceId where the query should be executed.
     * @param query The Kusto query to fetch the logs.
     * @param timeSpan The time period for which the logs should be looked up.
     * @param options The log query options to configure server timeout, set additional workspaces or enable
     * statistics and rendering information in response.
     * @return The logs matching the query.
     */
    @ServiceMethod(returns = ReturnType.SINGLE)
    public Mono<Response<LogsQueryResult>> queryLogsWithResponse(String workspaceId, String query,
                                                                 QueryTimeSpan timeSpan, LogsQueryOptions options) {
        return withContext(context -> queryLogsWithResponse(workspaceId, query, timeSpan, options, context));
    }

    /**
     * Returns all the Azure Monitor logs matching the given batch of queries in the specified workspaceId.
     * @param workspaceId The workspaceId where the batch of queries should be executed.
     * @param queries A batch of Kusto queries.
     * @param timeSpan The time period for which the logs should be looked up.
     * @return A collection of query results corresponding to the input batch of queries.
     */
    @ServiceMethod(returns = ReturnType.SINGLE)
    public Mono<LogsBatchQueryResultCollection> queryLogsBatch(String workspaceId, List<String> queries,
                                                               QueryTimeSpan timeSpan) {
        LogsBatchQuery logsBatchQuery = new LogsBatchQuery();
        queries.forEach(query -> logsBatchQuery.addQuery(workspaceId, query, timeSpan));
        return queryLogsBatchWithResponse(logsBatchQuery).map(Response::getValue);
    }

    /**
     * Returns all the Azure Monitor logs matching the given batch of queries.
     * @param logsBatchQuery {@link LogsBatchQuery} containing a batch of queries.
     * @return A collection of query results corresponding to the input batch of queries.@return
     */
    @ServiceMethod(returns = ReturnType.SINGLE)
    public Mono<Response<LogsBatchQueryResultCollection>> queryLogsBatchWithResponse(LogsBatchQuery logsBatchQuery) {
        return queryLogsBatchWithResponse(logsBatchQuery, Context.NONE);
    }

    Mono<Response<LogsBatchQueryResultCollection>> queryLogsBatchWithResponse(LogsBatchQuery logsBatchQuery, Context context) {
        List<BatchQueryRequest> requests = LogsQueryHelper.getBatchQueries(logsBatchQuery);
        Duration maxServerTimeout = LogsQueryHelper.getMaxServerTimeout(logsBatchQuery);
        if (maxServerTimeout != null) {
            context = context.addData(AZURE_RESPONSE_TIMEOUT, maxServerTimeout.plusSeconds(CLIENT_TIMEOUT_BUFFER));
        }

        BatchRequest batchRequest = new BatchRequest(requests);

        return innerClient.getQueries().batchWithResponseAsync(batchRequest, context)
                .onErrorMap(ex -> {
                    if (ex instanceof ErrorResponseException) {
                        ErrorResponseException error = (ErrorResponseException) ex;
                        ErrorInfo errorInfo = error.getValue().getError();
                        return new LogsQueryException(error.getResponse(), mapLogsQueryError(errorInfo));
                    }
                    return ex;
                })
                .map(this::convertToLogQueryBatchResult);
    }

    private Context updateContext(Duration serverTimeout, Context context) {
        if (serverTimeout != null) {
            return context.addData(AZURE_RESPONSE_TIMEOUT, serverTimeout.plusSeconds(CLIENT_TIMEOUT_BUFFER));
        }
        return context;
    }

    private Response<LogsBatchQueryResultCollection> convertToLogQueryBatchResult(Response<BatchResponse> response) {
        List<LogsBatchQueryResult> batchResults = new ArrayList<>();
        LogsBatchQueryResultCollection logsBatchQueryResultCollection = new LogsBatchQueryResultCollection(batchResults);

        BatchResponse batchResponse = response.getValue();

        for (BatchQueryResponse singleQueryResponse : batchResponse.getResponses()) {

            BatchQueryResults queryResults = singleQueryResponse.getBody();
            LogsQueryResult logsQueryResult = getLogsQueryResult(queryResults.getTables(),
                    queryResults.getStatistics(), queryResults.getRender(), queryResults.getError());
            LogsBatchQueryResult logsBatchQueryResult = new LogsBatchQueryResult(singleQueryResponse.getId(),
                    singleQueryResponse.getStatus(), logsQueryResult);
            batchResults.add(logsBatchQueryResult);
        }
        batchResults.sort(Comparator.comparingInt(o -> Integer.parseInt(o.getId())));
        return new SimpleResponse<>(response.getRequest(), response.getStatusCode(), response.getHeaders(), logsBatchQueryResultCollection);
    }

    private LogsQueryError mapLogsQueryError(ErrorInfo errors) {
        if (errors != null) {
            List<LogsQueryErrorDetail> errorDetails = Collections.emptyList();
            if (errors.getDetails() != null) {
                errorDetails = errors.getDetails()
                        .stream()
                        .map(errorDetail -> new LogsQueryErrorDetail(errorDetail.getCode(),
                                errorDetail.getMessage(),
                                errorDetail.getTarget(),
                                errorDetail.getValue(),
                                errorDetail.getResources(),
                                errorDetail.getAdditionalProperties()))
                        .collect(Collectors.toList());
            }

            ErrorInfo innerError = errors.getInnererror();
            ErrorInfo currentError = errors.getInnererror();
            while (currentError != null) {
                innerError = currentError.getInnererror();
                currentError = currentError.getInnererror();
            }
            String code = errors.getCode();
            if (errors.getCode() != null && innerError != null && errors.getCode().equals(innerError.getCode())) {
                code = innerError.getCode();
            }
            return new LogsQueryError(errors.getMessage(), code, errorDetails);
        }
        return null;
    }

    Mono<Response<LogsQueryResult>> queryLogsWithResponse(String workspaceId, String query, QueryTimeSpan timeSpan,
                                                          LogsQueryOptions options, Context context) {
        String preferHeader = LogsQueryHelper.buildPreferHeaderString(options);
        context = updateContext(options.getServerTimeout(), context);

        QueryBody queryBody = new QueryBody(query);
        if (timeSpan != null) {
            queryBody.setTimespan(timeSpan.toString());
        }
        queryBody.setWorkspaces(getAllWorkspaces(options));
        return innerClient
                .getQueries()
                .executeWithResponseAsync(workspaceId,
                        queryBody,
                        preferHeader,
                        context)
                .onErrorMap(ex -> {
                    if (ex instanceof ErrorResponseException) {
                        ErrorResponseException error = (ErrorResponseException) ex;
                        ErrorInfo errorInfo = error.getValue().getError();
                        return new LogsQueryException(error.getResponse(), mapLogsQueryError(errorInfo));
                    }
                    return ex;
                })
                .map(this::convertToLogQueryResult);
    }

    private Response<LogsQueryResult> convertToLogQueryResult(Response<QueryResults> response) {
        QueryResults queryResults = response.getValue();
        LogsQueryResult logsQueryResult = getLogsQueryResult(queryResults.getTables(), queryResults.getStatistics(),
                queryResults.getRender(), queryResults.getError());
        return new SimpleResponse<>(response.getRequest(), response.getStatusCode(),
                response.getHeaders(), logsQueryResult);
    }

    private LogsQueryResult getLogsQueryResult(List<Table> innerTables, Object innerStats,
                                               Object innerVisualization, ErrorInfo innerError) {
        List<LogsTable> tables = null;

        if (innerTables != null) {
            tables = new ArrayList<>();
            for (Table table : innerTables) {
                List<LogsTableCell> tableCells = new ArrayList<>();
                List<LogsTableRow> tableRows = new ArrayList<>();
                List<LogsTableColumn> tableColumns = new ArrayList<>();
                LogsTable logsTable = new LogsTable(tableCells, tableRows, tableColumns);
                tables.add(logsTable);
                List<List<Object>> rows = table.getRows();

                for (int i = 0; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    LogsTableRow tableRow = new LogsTableRow(i, new ArrayList<>());
                    tableRows.add(tableRow);
                    for (int j = 0; j < row.size(); j++) {
                        LogsTableCell cell = new LogsTableCell(table.getColumns().get(j).getName(),
                                table.getColumns().get(j).getType(), j, i, row.get(j));
                        tableCells.add(cell);
                        tableRow.getRow().add(cell);
                    }
                }
            }
        }

        LogsQueryStatistics queryStatistics = null;

        if (innerStats != null) {
            queryStatistics = new LogsQueryStatistics(innerStats);
        }

        LogsQueryVisualization queryVisualization = null;
        if (innerVisualization != null) {
            queryVisualization = new LogsQueryVisualization(innerVisualization);
        }

        LogsQueryResult logsQueryResult = new LogsQueryResult(tables, queryStatistics, queryVisualization,
                mapLogsQueryError(innerError));
        return logsQueryResult;
    }

    private List<String> getAllWorkspaces(LogsQueryOptions body) {
        List<String> allWorkspaces = new ArrayList<>();
        if (!CoreUtils.isNullOrEmpty(body.getAdditionalWorkspaces())) {
            allWorkspaces.addAll(body.getAdditionalWorkspaces());
        }
        return allWorkspaces;
    }
}
