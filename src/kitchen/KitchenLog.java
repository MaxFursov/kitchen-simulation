package kitchen;

/**
 * Простий журнал подій. Кожен рядок починається з віртуального часу,
 * щоб результат було легко читати людині, яка не є програмістом.
 */
public class KitchenLog {

    private final SimClock clock;

    public KitchenLog(SimClock clock) {
        this.clock = clock;
    }

    /** Подія від імені конкретної дійової особи (кухар, клієнт...). */
    public void say(String who, String message) {
        // Рядок формується повністю й виводиться одним викликом println,
        // тому повідомлення різних потоків не перемішуються.
        System.out.println("[" + clock.nowText() + "] " + who + ": " + message);
    }

    /** Загальне повідомлення без конкретного виконавця. */
    public void info(String message) {
        System.out.println("[" + clock.nowText() + "] *** " + message);
    }
}
