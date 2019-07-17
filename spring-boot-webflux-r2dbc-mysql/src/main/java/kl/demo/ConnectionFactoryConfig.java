package kl.demo;

import com.github.jasync.r2dbc.mysql.JasyncConnectionFactory;
import com.github.jasync.sql.db.mysql.pool.MySQLConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * @author: kl @kailing.pub
 * @date: 2019/7/16
 */
@Configuration
@ConfigurationProperties(prefix = "spring.myr2dbc")
@EnableR2dbcRepositories
class ConnectionFactoryConfig extends AbstractR2dbcConfiguration {

    private String username;
    private String password;
    private Integer port;
    private String host;
    private String database;

    @Override
    public ConnectionFactory connectionFactory() {
        com.github.jasync.sql.db.Configuration config = new com.github.jasync.sql.db.Configuration(
                username,
                host,
                port,
                password,
                database
        );
        return new JasyncConnectionFactory(new MySQLConnectionFactory(config));
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }
}