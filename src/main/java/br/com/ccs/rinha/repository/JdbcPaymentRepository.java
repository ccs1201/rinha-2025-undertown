package br.com.ccs.rinha.repository;

import br.com.ccs.rinha.api.model.input.PaymentRequest;
import br.com.ccs.rinha.api.model.output.PaymentSummary;
import br.com.ccs.rinha.config.DataSourceFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


public class JdbcPaymentRepository {

    private static final Logger log = Logger.getLogger(JdbcPaymentRepository.class.getName());
    private static JdbcPaymentRepository instance;
    private static DataSource dataSource;

    private static final String SQL_INSERT = "INSERT INTO payments (correlation_id, amount, requested_at, is_default) VALUES (?, ?, ?, ?)";
    private static final String SQL_SUMMARY = """
            SELECT 
                SUM(CASE WHEN is_default = true THEN 1 ELSE 0 END) as default_count,
                SUM(CASE WHEN is_default = true THEN amount ELSE 0 END) as default_amount,
                SUM(CASE WHEN is_default = false THEN 1 ELSE 0 END) as fallback_count,
                SUM(CASE WHEN is_default = false THEN amount ELSE 0 END) as fallback_amount
            FROM payments 
            WHERE requested_at >= ? AND requested_at <= ?
            """;
    private ArrayBlockingQueue<PaymentRequest> queues[];


    public static JdbcPaymentRepository getInstance() {
        if (instance == null) {
            instance = new JdbcPaymentRepository();
        }
        return instance;
    }

    public JdbcPaymentRepository() {
        queues = new ArrayBlockingQueue[DataSourceFactory.getPoolSize() - 1];
        initialize();
    }

    private void initialize() {
        dataSource = DataSourceFactory.getInstance();
        log.info("JdbcPaymentRepository initialized");
        var poolSize = DataSourceFactory.getPoolSize() - 1;

        for (int i = 0; i < poolSize; i++) {
            var queue = new ArrayBlockingQueue<PaymentRequest>(1_000);
            queues[i] = queue;
            startWorker(i, queue);
        }
    }

    private void startWorker(int workerIndex, ArrayBlockingQueue<PaymentRequest> queue) {
        log.info(String.format("Starting repository-worker-%s", workerIndex));
        Thread.ofVirtual().name("repository-worker-" + workerIndex).start(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (queue.size() >= 10) {
                            long now = Instant.now().toEpochMilli();
                            List<PaymentRequest> batch = new ArrayList<>(100);
                            queue.drainTo(batch, 100);

                            if (batch.isEmpty()) continue;

                            for (PaymentRequest pr : batch) {
                                stmt.setObject(1, pr.correlationId);
                                stmt.setBigDecimal(2, pr.amount);
                                stmt.setObject(3, Timestamp.from(pr.requestedAt));
                                stmt.setBoolean(4, pr.isDefault);
                                stmt.addBatch();
                            }

                            stmt.executeBatch();
                            conn.commit();

                            long elapsed = Instant.now().toEpochMilli() - now;
                            log.info(String.format("BATCH Size %s Processed in %sms Queue size %s",
                                    batch.size(), elapsed, queue.size()));
                            continue;
                        }

                        // fallback para unitário
                        PaymentRequest pr = queue.take();
                        stmt.setObject(1, pr.correlationId);
                        stmt.setBigDecimal(2, pr.amount);
                        stmt.setObject(3, Timestamp.from(pr.requestedAt));
                        stmt.setBoolean(4, pr.isDefault);
                        stmt.executeUpdate();
                        conn.commit();

                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Error inserting payment", e);
                    }
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Worker failure", e);
            }
        });
        log.info(String.format("repository-worker-%s started", workerIndex));
    }


    public void save(PaymentRequest paymentRequest) {
        int index = Math.abs(paymentRequest.hashCode()) % queues.length;
        boolean accepted = queues[index].offer(paymentRequest);
        if (!accepted) {
            log.info(String.format("Payment rejected by queues"));
        }

//        try (Connection conn = dataSource.getConnection();
//             PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {
//            stmt.setObject(1, request.correlationId);
//            stmt.setBigDecimal(2, request.amount);
//            stmt.setObject(3, Timestamp.from(request.requestedAt));
//            stmt.setBoolean(4, request.isDefault);
//            stmt.execute();
//            conn.commit();
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }


    public PaymentSummary getSummary(OffsetDateTime from, OffsetDateTime to) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SQL_SUMMARY)) {

            stmt.setObject(1, from);
            stmt.setObject(2, to);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PaymentSummary(
                            new PaymentSummary.Summary(rs.getLong("default_count"),
                                    rs.getBigDecimal("default_amount") != null ? rs.getBigDecimal("default_amount") : BigDecimal.ZERO),
                            new PaymentSummary.Summary(rs.getLong("fallback_count"),
                                    rs.getBigDecimal("fallback_amount") != null ? rs.getBigDecimal("fallback_amount") : BigDecimal.ZERO)
                    );
                }
                return new PaymentSummary(new PaymentSummary.Summary(0, BigDecimal.ZERO), new PaymentSummary.Summary(0, BigDecimal.ZERO));
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void purge() {
        String sql = "DELETE FROM payments";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}