package kitchen;

import java.util.Map;

/**
 * Монітор станів потоків. Раз на кілька секунд виводить таблицю:
 * який кухар у якому стані (Thread.State) і що він робить простими словами.
 *
 * Стани потоку в Java:
 *   NEW           — потік створено, але ще не запущено
 *   RUNNABLE      — потік виконується
 *   BLOCKED       — чекає доступу до заблокованого ресурсу (synchronized)
 *   WAITING       — чекає без обмеження в часі (наприклад, на вільну плиту в семафорі)
 *   TIMED_WAITING — чекає певний час (sleep: готує страву або чекає початку зміни)
 *   TERMINATED    — потік завершив роботу
 */
public class ThreadStateMonitor implements Runnable {

    private final Map<Thread, Cook> cooks;
    private final KitchenLog log;
    private final long periodMillis;

    public ThreadStateMonitor(Map<Thread, Cook> cooks, KitchenLog log, long periodMillis) {
        this.cooks = cooks;
        this.log = log;
        this.periodMillis = periodMillis;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(periodMillis);
                printStates("Стан кухарів зараз");
            }
        } catch (InterruptedException e) {
            // Монітор просто зупиняють наприкінці програми
            Thread.currentThread().interrupt();
        }
    }

    /** Вивести таблицю станів усіх кухарів. */
    public void printStates(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(title).append(" ---");
        for (Map.Entry<Thread, Cook> entry : cooks.entrySet()) {
            Thread.State state = entry.getKey().getState();
            sb.append("\n      ").append(entry.getKey().getName())
              .append(" | стан потоку: ").append(state)
              .append(" (").append(describe(state)).append(")")
              .append(" | ").append(entry.getValue().getActivity());
        }
        log.info(sb.toString());
    }

    /** Пояснення стану потоку українською. */
    public static String describe(Thread.State state) {
        switch (state) {
            case NEW:           return "ще не запущений";
            case RUNNABLE:      return "виконується";
            case BLOCKED:       return "заблокований";
            case WAITING:       return "очікує без таймера";
            case TIMED_WAITING: return "очікує певний час";
            case TERMINATED:    return "завершив роботу";
            default:            return "невідомо";
        }
    }
}
