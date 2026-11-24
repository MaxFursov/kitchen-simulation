package kitchen;

import java.util.concurrent.TimeUnit;

/**
 * Клієнт ресторану. Реалізує Runnable: приходить у заданий час,
 * робить замовлення й чекає на страву (або на відмову).
 */
public class Client implements Runnable {

    private final String name;
    private final Dish dish;
    private final double arrivalMinutes; // коли клієнт прийде (хвилини від півночі)
    private final Kitchen kitchen;

    public Client(String name, Dish dish, String arrivalTime, Kitchen kitchen) {
        this.name = name;
        this.dish = dish;
        this.arrivalMinutes = SimClock.parse(arrivalTime);
        this.kitchen = kitchen;
    }

    public String getName() {
        return name;
    }

    @Override
    public void run() {
        KitchenLog log = kitchen.getLog();
        String who = "Клієнт " + name;
        try {
            kitchen.getClock().sleepUntil(arrivalMinutes);
            log.say(who, "приходить і хоче замовити «" + dish + "»");

            Order order = new Order(name, dish);
            try {
                kitchen.placeOrder(order);
            } catch (KitchenClosedException e) {
                // Очікувана ситуація: клієнт прийшов у неробочий час
                log.say(who, "отримує відмову — " + e.getMessage() + ". Йде ні з чим");
                return;
            }
            log.say(who, "чекає на замовлення №" + order.getId());

            // Чекаємо не безкінечно: якщо щось піде не так, потік не «зависне» назавжди
            if (!order.awaitFinish(60, TimeUnit.SECONDS)) {
                log.say(who, "занадто довго чекає і йде");
                return;
            }
            if (order.getStatus() == Order.Status.DONE) {
                log.say(who, "отримує «" + dish + "», дякує й смачно обідає");
            } else {
                log.say(who, "замовлення №" + order.getId() + " скасовано через закриття кухні. Йде голодним");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.say(who, "пішов, не дочекавшись (потік перервано)");
        }
    }
}
