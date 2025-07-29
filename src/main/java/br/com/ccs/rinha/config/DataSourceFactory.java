package br.com.ccs.rinha.config;

import br.com.ccs.rinha.exception.DatasourceException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import static java.util.Objects.isNull;


public class DataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(DataSourceFactory.class);
    private static final DataSource instance;
    private static int poolSize;

    static {
        instance = initDataSource();
    }

    private DataSourceFactory() {
    }

    public static int getPoolSize() {
        return poolSize;
    }

    public static DataSource getInstance() {
        return instance;
    }

    private static DataSource initDataSource() {
        String minIdleEnv = System.getenv("DATASOURCE_MINIMUM_IDLE").trim();
        String maxPoolEnv = System.getenv("DATASOURCE_MAXIMUM_POOL_SIZE").trim();
        String timeoutEnv = System.getenv("DATASOURCE_TIMEOUT").trim();


        int minIdle = Integer.parseInt(minIdleEnv);
        int maxPoolSize = Integer.parseInt(maxPoolEnv);
        int dataSourceTimeout = Integer.parseInt(timeoutEnv);

        poolSize = maxPoolSize;

        String dataSourceUrl = System.getenv("DATASOURCE_URL").trim();
        String datasourceUsername = System.getenv("DATASOURCE_USERNAME").trim();
        String dataSourcePassword = System.getenv("DATASOURCE_PASSWORD").trim();

        log.info("Data Source URL: {}", dataSourceUrl);
        log.info("Data Source Username: {}", datasourceUsername);
        log.info("Data Source Password: {}", dataSourcePassword);
        log.info("Data Source Timeout: {}", dataSourceTimeout);
        log.info("Data Source Minimum Idle: {}", minIdle);
        log.info("Data Source Maximum Pool Size: {}", maxPoolSize);

        validate(dataSourceUrl, datasourceUsername, dataSourcePassword);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceUrl);
        config.setUsername(datasourceUsername);
        config.setPassword(dataSourcePassword);
        config.setMinimumIdle(minIdle);
        config.setMaximumPoolSize(maxPoolSize);
        config.setConnectionTimeout(dataSourceTimeout);
        config.setAutoCommit(false);
        config.setValidationTimeout(1000);
        log.info("Data Source Configured {}", config);

        return new HikariDataSource(config);
    }

    private static void validate(String dataSourceUrl, String datasourceUsername, String dataSourcePassword) {
        if (isNull(dataSourceUrl) || dataSourceUrl.isBlank()) {
            throw new DatasourceException("Data Source URL must not be null");
        }
        if (isNull(datasourceUsername) || datasourceUsername.isBlank()) {
            throw new DatasourceException("Data Source Username must not be null");
        }
        if (isNull(dataSourcePassword) || dataSourcePassword.isBlank()) {
            throw new DatasourceException("Data Source Password must not be null");
        }
    }
}
