package kg.megalab.pivnitsabackend.repository;

import kg.megalab.pivnitsabackend.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByBookingIdAndProviderOrderByIdDesc(Long bookingId, String provider);

    boolean existsByBookingIdAndProvider(Long bookingId, String provider);
}
