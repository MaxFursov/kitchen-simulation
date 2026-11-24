package kitchen;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Замовлення клієнта. Клієнт після подачі замовлення чекає на нього
 * (через CountDownLatch), а кухар повідомляє, коли замовлення завершено
 * або скасовано.
 */
public class Order {

    /** Етапи життя замовлення. */
    public enum Status { ACCEPTED, COOKING, DONE, CANCELLED }

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final int id = COUNTER.incrementAndGet();
    private final String clientName;
    private final Dish dish;
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile Status status = Status.ACCEPTED;

    public Order(String clientName, Dish dish) {
        this.clientName = clientName;
        this.dish = dish;
    }

    public int getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public Dish getDish() {
        return dish;
    }

    public Status getStatus() {
        return status;
    }

    /** Кухар поставив страву на плиту. */
    public synchronized void markCooking() {
        if (status == Status.ACCEPTED) {
            status = Status.COOKING;
        }
    }

    /**
     * Завершує замовлення (DONE або CANCELLED) і будить клієнта.
     * Спрацьовує лише один раз; повертає true, якщо саме цей виклик завершив замовлення.
     */
    public synchronized boolean finish(Status finalStatus) {
        if (status == Status.DONE || status == Status.CANCELLED) {
            return false;
        }
        status = finalStatus;
        finished.countDown();
        return true;
    }

    /** Клієнт чекає на завершення замовлення не довше заданого часу. */
    public boolean awaitFinish(long timeout, TimeUnit unit) throws InterruptedException {
        return finished.await(timeout, unit);
    }
}
