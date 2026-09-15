package kg.megalab.pivnitsabackend.exception.booking;

public class BookingNotOwnedException extends RuntimeException {
    public BookingNotOwnedException(String message) {
        super(message);
    }
}