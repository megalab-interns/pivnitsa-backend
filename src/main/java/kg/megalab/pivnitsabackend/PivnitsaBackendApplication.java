package kg.megalab.pivnitsabackend;

import kg.megalab.pivnitsabackend.config.ManualPaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(ManualPaymentProperties.class)
public class PivnitsaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PivnitsaBackendApplication.class, args);
    }

}
