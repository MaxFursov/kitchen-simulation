package kitchen;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Кухня ресторану.
 *
 * Головна ідея: плит обмежена кількість (наприклад, 4), а кухарів може бути більше.
 * Семафор (Semaphore) працює як «лічильник вільних плит»:
 *   - acquire() — кухар займає плиту (якщо вільних немає, він чекає);
 *   - release() — кухар звільняє плиту після приготування.
 *
 * Також кухня має робочі години: до відкриття та після закриття
 * замовлення не приймаються і не готуються.
 */
public class Kitchen {

    private final SimClock clock;
    private final KitchenLog log;
    private final int stoveCount;
    private final double openAt;   // час відкриття (хвилини від півночі)
    private final double closeAt;  // час закриття

    /** Семафор плит. Параметр true = «чесна» черга: хто перший чекає, той перший отримує плиту. */
    private final Semaphore stoves;

    /** Черга прийнятих замовлень, які ще не взяв жоден кухар. */
    private final BlockingQueue<Order> pendingOrders = new LinkedBlockingQueue<>();

    // Лічильники для підсумку (AtomicInteger безпечні для багатьох потоків)
    private final AtomicInteger acceptedCount = new AtomicInteger();
    private final AtomicInteger cookedCount = new AtomicInteger();
    private final AtomicInteger cancelledCount = new AtomicInteger();
    private final AtomicInteger rejectedCount = new AtomicInteger();
    private final AtomicInteger busyStoves = new AtomicInteger();
    private final AtomicInteger maxBusyStoves = new AtomicInteger();

    public Kitchen(SimClock clock, KitchenLog log, int stoveCount, int openAtMinutes, int closeAtMinutes) {
        if (stoveCount <= 0) {
            throw new IllegalArgumentException("Кількість плит має бути додатною");
        }
        if (closeAtMinutes <= openAtMinutes) {
            throw new IllegalArgumentException("Кухня має закриватися пізніше, ніж відкривається");
        }
        this.clock = clock;
        this.log = log;
        this.stoveCount = stoveCount;
        this.openAt = openAtMinutes;
        this.closeAt = closeAtMinutes;
        this.stoves = new Semaphore(stoveCount, true);
    }

    public SimClock getClock() {
        return clock;
    }

    public KitchenLog getLog() {
        return log;
    }

    public double getOpenAt() {
        return openAt;
    }

    public double getCloseAt() {
        return closeAt;
    }

    public int getStoveCount() {
        return stoveCount;
    }

    /** Чи працює кухня просто зараз. */
    public boolean isOpen() {
        double now = clock.nowMinutes();
        return now >= openAt && now < closeAt;
    }

    /**
     * Прийняти замовлення від клієнта.
     * Метод synchronized, щоб замовлення не «проскочило» в чергу в момент закриття кухні
     * (див. closeAndCancelPending).
     *
     * @throws KitchenClosedException якщо кухня зараз не працює
     */
    public synchronized void placeOrder(Order order) throws KitchenClosedException {
        if (!isOpen()) {
            rejectedCount.incrementAndGet();
            if (clock.nowMinutes() < openAt) {
                throw new KitchenClosedException("кухня ще не відчинена (працює з "
                        + SimClock.format(openAt) + ")");
            }
            throw new KitchenClosedException("кухня вже зачинена (працювала до "
                    + SimClock.format(closeAt) + ")");
        }
        // Спочатку пишемо в журнал, потім кладемо в чергу — щоб запис «прийнято»
        // завжди йшов раніше, ніж кухар «візьме» замовлення
        log.say("Кухня", "приймає замовлення №" + order.getId() + " «" + order.getDish()
                + "» від клієнта " + order.getClientName());
        pendingOrders.add(order);
        acceptedCount.incrementAndGet();
    }

    /** Кухар бере наступне замовлення з черги (чекає не довше waitMillis реальних мілісекунд). */
    public Order takeOrder(long waitMillis) throws InterruptedException {
        return pendingOrders.poll(waitMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Приготувати страву: зайняти плиту, приготувати, звільнити плиту.
     * Якщо вільної плити немає, кухар чекає на семафорі.
     * Плита ЗАВЖДИ звільняється (блок finally), навіть якщо сталася помилка.
     */
    public void cook(Order order, Cook cook) throws InterruptedException {
        String cookName = "Кухар " + cook.getName();
        // 1. Займаємо плиту (тут потік може перейти у стан WAITING, якщо всі плити зайняті)
        try {
            stoves.acquire();
        } catch (InterruptedException e) {
            cancel(order);
            throw e;
        }

        try {
            // 2. Поки кухар чекав плиту, кухня могла зачинитися — тоді страву не готуємо
            if (!isOpen()) {
                cancel(order);
                log.say(cookName, "кухня зачинилась, а вільної плити так і не з'явилось — замовлення №"
                        + order.getId() + " (" + order.getDish() + ") скасовано");
                return;
            }

            // 3. Готуємо
            cook.setActivity("готує замовлення №" + order.getId() + " на плиті");
            order.markCooking();
            int busy = busyStoves.incrementAndGet();
            maxBusyStoves.accumulateAndGet(busy, Math::max);
            log.say(cookName, "ставить на плиту «" + order.getDish() + "» для клієнта "
                    + order.getClientName() + " (замовлення №" + order.getId()
                    + "). Зайнято плит: " + busy + " з " + stoveCount);
            try {
                clock.sleepSimMinutes(order.getDish().getCookingMinutes());
                if (order.finish(Order.Status.DONE)) {
                    cookedCount.incrementAndGet();
                }
                log.say(cookName, "страва «" + order.getDish() + "» готова! Плита звільнилась");
            } catch (InterruptedException e) {
                cancel(order);
                throw e;
            } finally {
                busyStoves.decrementAndGet();
            }
        } finally {
            // 4. Плиту звільняємо в будь-якому випадку
            stoves.release();
        }
    }

    /**
     * Викликається, коли кухня закривається: усі замовлення, які ще не взяли в роботу,
     * скасовуються. Метод synchronized разом із placeOrder, тому нові замовлення
     * після цього вже не потраплять у чергу.
     */
    public synchronized void closeAndCancelPending() {
        Order order;
        while ((order = pendingOrders.poll()) != null) {
            cancel(order);
            log.info("Кухня зачинилась: замовлення №" + order.getId() + " ("
                    + order.getDish() + ", " + order.getClientName() + ") не встигли взяти в роботу — скасовано");
        }
    }

    /** Скасувати замовлення і розбудити клієнта, який його чекає. */
    private void cancel(Order order) {
        if (order.finish(Order.Status.CANCELLED)) {
            cancelledCount.incrementAndGet();
        }
    }

    /** Текст підсумків роботи кухні. */
    public String summary() {
        return "Прийнято замовлень: " + acceptedCount.get()
                + "\n  з них приготовано: " + cookedCount.get()
                + "\n  з них скасовано через закриття кухні: " + cancelledCount.get()
                + "\nВідхилено (кухня не працювала): " + rejectedCount.get()
                + "\nНайбільше плит одночасно: " + maxBusyStoves.get() + " з " + stoveCount
                + (maxBusyStoves.get() <= stoveCount ? " (ліміт не порушено)" : " (УВАГА: ліміт порушено!)");
    }
}
