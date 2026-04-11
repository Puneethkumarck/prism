package com.stablebridge.prism.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.infrastructure.grpc.proto.geyser.GeyserGrpc;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.GeyserProto;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.PingRequest;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.PongResponse;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;

class GrpcChannelFactoryIntegrationTest {

    static WebServer server;
    static String endpoint;

    @BeforeAll
    static void startServer() {
        server = WebServer.builder()
                .port(0)
                .addRouting(GrpcRouting.builder()
                        .unary(
                                GeyserProto.getDescriptor(),
                                "Geyser",
                                "Ping",
                                (PingRequest request, io.grpc.stub.StreamObserver<PongResponse> responseObserver) -> {
                                    responseObserver.onNext(PongResponse.newBuilder()
                                            .setCount(request.getCount())
                                            .build());
                                    responseObserver.onCompleted();
                                }))
                .build()
                .start();
        endpoint = "http://localhost:" + server.port();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldCompleteUnaryRoundTripThroughFactoryChannel() {
        // given
        var channel = GrpcChannelFactory.create(endpoint);
        var stub = GeyserGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .withMaxInboundMessageSize(GrpcChannelFactory.MAX_DECODE_MESSAGE_SIZE);
        var request = PingRequest.newBuilder().setCount(42).build();

        // when
        var response = stub.ping(request);

        // then
        var expected = PongResponse.newBuilder().setCount(42).build();
        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }
}
