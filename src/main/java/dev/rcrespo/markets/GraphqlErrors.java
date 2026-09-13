package dev.rcrespo.markets;

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.SimpleDataFetcherExceptionHandler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static dev.rcrespo.markets.Domain.BusinessException;

@Component
public class GraphqlErrors extends SimpleDataFetcherExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GraphqlErrors.class);

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters parameters) {
        Throwable exception = parameters.getException();
        while (exception.getCause() != null && !(exception instanceof BusinessException)) exception = exception.getCause();
        String message = "Unexpected server error";
        String code = "INTERNAL_ERROR";
        if (exception instanceof BusinessException business) {
            message = business.getMessage();
            code = business.code();
        } else {
            LOG.error("GraphQL data fetch failed", parameters.getException());
        }
        var error = GraphqlErrorBuilder.newError(parameters.getDataFetchingEnvironment())
                .message(message).extensions(Map.of("code", code)).build();
        return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build());
    }
}
