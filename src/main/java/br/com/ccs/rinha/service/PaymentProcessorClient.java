package br.com.ccs.rinha.service;

import br.com.ccs.rinha.api.model.input.PaymentRequest;
import br.com.ccs.rinha.config.ExecutorConfig;
import br.com.ccs.rinha.exception.HttpClientException;
import br.com.ccs.rinha.repository.JdbcPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;

public class PaymentProcessorClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorClient.class);

    private static final String contentType = "Content-Type";
    private static final String contentTypeValue = "application/json";

    private final JdbcPaymentRepository repository;
    private final ExecutorService executorService;
    private final HttpClient httpClient;
    private static final PaymentProcessorClient instance;
    private final URI defaultUri;
    private final URI fallbackUri;
    private final ArrayBlockingQueue<PaymentRequest> queues[];

    static {
        instance = new PaymentProcessorClient();
    }

    public static PaymentProcessorClient getInstance() {
        return instance;
    }


    private PaymentProcessorClient() {
        var defaultUrl = System.getenv("payment-processor-default-url").trim();
        defaultUrl = defaultUrl.concat("/payments");

        var fallbackUrl = System.getenv("payment-processor-fallback-url").trim();
        fallbackUrl = fallbackUrl.concat("/payments");

        var workers = Integer.parseInt(System.getenv("PAYMENT_PROCESSOR_WORKERS"));

        this.repository = JdbcPaymentRepository.getInstance();
        this.executorService = ExecutorConfig.getExecutor();

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(1000))
                .build();

        defaultUri = URI.create(defaultUrl);
        fallbackUri = URI.create(fallbackUrl);

        queues = new ArrayBlockingQueue[workers];

        for (int i = 0; i < workers; i++) {
            var queue = queues[i] = new ArrayBlockingQueue<>(1_000);
            startProcessQueue(i, queue);
        }

        log.info("Default service URL: {}", defaultUrl);
        log.info("Fallback fallback URL: {}", fallbackUrl);
    }

    private void startProcessQueue(int wokerIndex, ArrayBlockingQueue<PaymentRequest> queue) {
        Thread.ofVirtual().name("payment-processor" + wokerIndex).start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processPaymentWithRetry(queue.take(), 0);
                } catch (InterruptedException e) {
                    log.error("worker: {} has error: {}", Thread.currentThread().getName(), e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void processPayment(PaymentRequest paymentRequest) {
        int index = Math.abs(paymentRequest.hashCode()) % queues.length;
        boolean accepted = queues[index].offer(paymentRequest);

        if (!accepted) {
            log.error("Payment rejected by queue {}", index);
        }
    }

    private void processPaymentWithRetry(PaymentRequest paymentRequest, int retryCount) {
        if (retryCount >= 3) {
//            log.error("Max retries reached for payment {}", paymentRequest.correlationId);
            return;
        }

        if (postToDefault(paymentRequest)) {
            repository.save(paymentRequest);
            return;
        }
//        log.error("Error processing payment default {} - retrying...", paymentRequest.correlationId);

        if (postToFallback(paymentRequest)) {
            repository.save(paymentRequest);
            return;
        }
//        log.error("Error processing payment fallback {} - retrying...", paymentRequest.correlationId);

        executorService.submit(() -> processPaymentWithRetry(paymentRequest, retryCount + 1));
    }


    private boolean postToDefault(PaymentRequest paymentRequest) {
        paymentRequest.setDefaultTrue();
        return doRequest(defaultUri, paymentRequest);
    }

    private boolean postToFallback(PaymentRequest paymentRequest) {
        paymentRequest.setDefaultFalse();
        return doRequest(fallbackUri, paymentRequest);
    }

    private Boolean doRequest(URI uri, PaymentRequest paymentRequest) throws HttpClientException {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header(contentType, contentTypeValue)
                    .version(HttpClient.Version.HTTP_2)
                    .timeout(java.time.Duration.ofMillis(5000))
                    .POST(HttpRequest.BodyPublishers.ofString(paymentRequest.getJson()))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                log.info("Error processing payment status code: {}", response.statusCode());
                return Boolean.FALSE;
            }
            return Boolean.TRUE;
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            return Boolean.FALSE;
        }
    }
}