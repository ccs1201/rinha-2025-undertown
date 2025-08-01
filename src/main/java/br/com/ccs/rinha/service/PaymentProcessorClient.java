package br.com.ccs.rinha.service;

import br.com.ccs.rinha.api.model.input.PaymentRequest;
import br.com.ccs.rinha.config.ExecutorConfig;
import br.com.ccs.rinha.repository.JdbcPaymentRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentProcessorClient {

    private static final Logger log = java.util.logging.Logger.getLogger(PaymentProcessorClient.class.getSimpleName());

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
        var defaultUrl = System.getenv("PAYMENT_PROCESSOR_DEFAULT_URL").trim();
        defaultUrl = defaultUrl.concat("/payments");

        var fallbackUrl = System.getenv("PAYMENT_PROCESSOR_FALLBACK_URL").trim();
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
            startWorkers(i, queue);
        }

        log.info("Default service URL: " + defaultUrl);
        log.info("Fallback fallback URL: " + fallbackUrl);
    }

    private void startWorkers(int wokerIndex, ArrayBlockingQueue<PaymentRequest> queue) {
        Thread.ofVirtual().name("payment-processor-" + wokerIndex).start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processPaymentWithRetry(queue.take());
                } catch (InterruptedException e) {
                    log.log(Level.SEVERE, String.format("worker: %s has error: %s", Thread.currentThread().getName(), e.getMessage()), e);
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void processPayment(PaymentRequest paymentRequest) {
        int index = Math.abs(paymentRequest.hashCode()) % queues.length;
        boolean accepted = queues[index].offer(paymentRequest);

        if (!accepted) {
            log.log(Level.SEVERE, String.format("Payment rejected by queue %s", index));
        }
    }

    private void processPaymentWithRetry(PaymentRequest paymentRequest) {
        postToDefault(paymentRequest);
    }


    private void postToDefault(PaymentRequest paymentRequest) {
        paymentRequest.setDefaultTrue();

        var request = HttpRequest.newBuilder()
                .uri(defaultUri)
                .header(contentType, contentTypeValue)
                .version(HttpClient.Version.HTTP_2)
                .timeout(java.time.Duration.ofMillis(3000))
                .POST(HttpRequest.BodyPublishers.ofString(paymentRequest.getJson()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenCompleteAsync((response, throwable) -> {
                    if (response.statusCode() != 200) {
                        postToFallback(paymentRequest);
//                        log.info(">>>>>>>>>>>>>>>> Falhou no default não deveria salvar!");
                        return;
                    }
//                    log.info("Default Salvando");
                    repository.save(paymentRequest);
                });
    }

    private void postToFallback(PaymentRequest paymentRequest) {
        paymentRequest.setDefaultFalse();
        var request = HttpRequest.newBuilder()
                .uri(fallbackUri)
                .header(contentType, contentTypeValue)
                .version(HttpClient.Version.HTTP_2)
                .timeout(java.time.Duration.ofMillis(1000))
                .POST(HttpRequest.BodyPublishers.ofString(paymentRequest.getJson()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenCompleteAsync((response, throwable) -> {
                    if (response.statusCode() != 200) {
                        processPayment(paymentRequest);
                        return;
                    }
                    repository.save(paymentRequest);
                });
    }
}