package kitchen;

/**
 * Кухар. Реалізує інтерфейс Runnable, тому його можна виконувати в окремому потоці:
 *     new Thread(new Cook(...)).start();
 *
 * Робочий день кухаря:
 *   1) чекає відкриття кухні;
 *   2) поки кухня відчинена — бере замовлення з черги й готує їх (на вільній плиті);
 *   3) після закриття доготовує те, що вже стоїть на плиті, і йде додому.
 */
public class Cook implements Runnable {

    private final String name;
    private final Kitchen kitchen;

    /** Що кухар робить зараз (для зрозумілого виводу монітора потоків). */
    private volatile String activity = "ще не прийшов на роботу";

    public Cook(String name, Kitchen kitchen) {
        this.name = name;
        this.kitchen = kitchen;
    }

    public String getName() {
        return name;
    }

    /** Кухня оновлює, що саме робить кухар (наприклад, коли він зайняв плиту). */
    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getActivity() {
        return activity;
    }

    @Override
    public void run() {
        KitchenLog log = kitchen.getLog();
        String who = "Кухар " + name;
        try {
            activity = "чекає відкриття кухні";
            kitchen.getClock().sleepUntil(kitchen.getOpenAt());
            log.say(who, "виходить на зміну, кухня відчинена");

            while (kitchen.isOpen()) {
                activity = "чекає на нове замовлення";
                Order order = kitchen.takeOrder(100); // коротке очікування, щоб вчасно помітити закриття
                if (order == null) {
                    continue;
                }
                activity = "узяв замовлення №" + order.getId() + ", чекає вільну плиту";
                log.say(who, "бере замовлення №" + order.getId() + " «" + order.getDish() + "»");
                kitchen.cook(order, this);
            }

            log.say(who, "кухня зачинена — нових замовлень не бере, іде додому");
        } catch (InterruptedException e) {
            // Потік перервали (наприклад, аварійне завершення програми)
            Thread.currentThread().interrupt();
            log.say(who, "роботу перервано");
        } finally {
            activity = "уже вдома (зміну завершено)";
        }
    }
}
