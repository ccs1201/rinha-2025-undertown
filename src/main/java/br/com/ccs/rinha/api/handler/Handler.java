package br.com.ccs.rinha.api.handler;

import br.com.ccs.rinha.api.model.input.PaymentRequest;
import br.com.ccs.rinha.config.ExecutorConfig;
import br.com.ccs.rinha.exception.HandlerException;
import br.com.ccs.rinha.repository.JdbcPaymentRepository;
import br.com.ccs.rinha.service.PaymentProcessorClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Handler implements HttpHandler {

    private final PaymentProcessorClient paymentProcessorClient;
    private final JdbcPaymentRepository paymentRepository;
    private final Executor executor;

    private final String postPaymentURI = "/payments";
    private final String getSummaryURI = "/payments-summary";
    private final String postPurgePaymentsURI = "/purge-payments";
    private final String emptyResnpose = "";
    private final HttpString responseContentType = new HttpString("Content-Type");

    private static Handler instance;

    public static Handler getInstance() {
        if (Handler.instance == null) {
            Handler.instance = new Handler();
        }
        return instance;
    }

    private Handler() {
        paymentRepository = JdbcPaymentRepository.getInstance();
        paymentProcessorClient = PaymentProcessorClient.getInstance();
        executor = ExecutorConfig.getExecutor();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            try {
                var requestURI = exchange.getRequestURI();

                if (requestURI.equals(postPaymentURI)) {
                    doPayment(ex, data);
                }

                if (requestURI.equals(getSummaryURI)) {
                    doSummary(exchange, ex);
                }

                if (requestURI.equals(postPurgePaymentsURI)) {
                    doPurge(ex);
                }
            } catch (Exception e) {
                throw new HandlerException(e);
            }
        });
    }

    private void doPayment(HttpServerExchange ex, byte[] data) {
        CompletableFuture.runAsync(() -> {
            try {
                paymentProcessorClient.processPayment(PaymentRequest.parse(new String(data, StandardCharsets.UTF_8)));
            } catch (Exception e) {
               e.printStackTrace();
            }
        }, executor);
        ex.setStatusCode(202);
        ex.getResponseSender().send(emptyResnpose);
    }

    private void doSummary(HttpServerExchange exchange, HttpServerExchange ex) {
        var from = OffsetDateTime.parse(exchange.getQueryParameters().get("from").getFirst());
        var to = OffsetDateTime.parse(exchange.getQueryParameters().get("to").getFirst());
        ex.setStatusCode(200);
        ex.getResponseHeaders().put(responseContentType, "application/json");
        ex.getResponseSender()
                .send(ByteBuffer
                        .wrap(paymentRepository.getSummary(from, to).toJson().getBytes(StandardCharsets.UTF_8)));
    }

    private void doPurge(HttpServerExchange ex) {
        paymentRepository.purge();
        ex.setStatusCode(200);
        ex.getResponseSender().send(emptyResnpose);
    }
}
