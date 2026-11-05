package kg.megalab.pivnitsabackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.manual-payment")
public record ManualPaymentProperties(
        Duration paymentWindow,
        String qrImageUrl,
        String whatsappNumber
) {
    public ManualPaymentProperties {
        paymentWindow = paymentWindow == null ? Duration.ofMinutes(15) : paymentWindow;
        if (paymentWindow.isZero() || paymentWindow.isNegative()) {
            throw new IllegalArgumentException("Payment window must be positive");
        }
        qrImageUrl = qrImageUrl == null ? "" : qrImageUrl;
        whatsappNumber = whatsappNumber == null ? "" : whatsappNumber;
    }
}
