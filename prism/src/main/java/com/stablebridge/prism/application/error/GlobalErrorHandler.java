package com.stablebridge.prism.application.error;

import java.sql.SQLException;
import java.util.NoSuchElementException;
import javax.inject.Singleton;

import com.stablebridge.prism.api.ErrorResponse;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GlobalErrorHandler {

    public static void register(HttpRouting.Builder routing) {
        routing.error(NoSuchElementException.class, (req, res, ex) ->
                        sendError(res, Status.NOT_FOUND_404, ex.getMessage()))
                .error(IllegalArgumentException.class, (req, res, ex) ->
                        sendError(res, Status.BAD_REQUEST_400, ex.getMessage()))
                .error(SQLException.class, (req, res, ex) -> {
                    log.error("Database error handling request", ex);
                    sendError(res, Status.INTERNAL_SERVER_ERROR_500, "Internal server error");
                });
    }

    private static void sendError(ServerResponse res, Status status, String message) {
        var body = ErrorResponse.builder()
                .error(message == null ? status.reasonPhrase() : message)
                .status(status.code())
                .build();
        res.headers().contentType(MediaTypes.APPLICATION_JSON);
        res.status(status).send(body);
    }
}
