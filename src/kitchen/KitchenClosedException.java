package kitchen;

/**
 * Виняток: замовлення неможливо прийняти, бо кухня зараз не працює
 * (ще не відчинилась або вже зачинилась).
 */
public class KitchenClosedException extends Exception {

    private static final long serialVersionUID = 1L;

    public KitchenClosedException(String message) {
        super(message);
    }
}
