package kitchen;

/**
 * Віртуальний годинник симуляції.
 *
 * Щоб не чекати цілий робочий день, час «прискорений»: наприклад, 20 хвилин
 * віртуального часу минають за 1 реальну секунду. Усі потоки користуються
 * одним годинником, тому бачать однаковий «час на кухні».
 */
public class SimClock {

    private final long startNanos = System.nanoTime(); // момент запуску (реальний)
    private final double startMinutes;                 // віртуальний час старту (хвилини від півночі)
    private final double minutesPerSecond;             // скільки віртуальних хвилин в 1 реальній секунді

    public SimClock(int startMinutes, double minutesPerSecond) {
        if (minutesPerSecond <= 0) {
            throw new IllegalArgumentException("Швидкість годинника має бути додатною");
        }
        this.startMinutes = startMinutes;
        this.minutesPerSecond = minutesPerSecond;
    }

    /** Поточний віртуальний час у хвилинах від півночі. */
    public double nowMinutes() {
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        return startMinutes + elapsedSeconds * minutesPerSecond;
    }

    /** Поточний віртуальний час у форматі ГГ:ХХ. */
    public String nowText() {
        return format(nowMinutes());
    }

    /** Заснути на задану кількість віртуальних хвилин (наприклад, поки готується страва). */
    public void sleepSimMinutes(double minutes) throws InterruptedException {
        long realMillis = (long) (minutes / minutesPerSecond * 1000);
        Thread.sleep(Math.max(0, realMillis));
    }

    /** Чекати, доки на годиннику не настане вказаний віртуальний час. */
    public void sleepUntil(double targetMinutes) throws InterruptedException {
        double remaining;
        while ((remaining = targetMinutes - nowMinutes()) > 0) {
            long realMillis = (long) Math.ceil(remaining / minutesPerSecond * 1000);
            Thread.sleep(Math.max(1, realMillis));
        }
    }

    /** Перетворює хвилини від півночі на рядок ГГ:ХХ. */
    public static String format(double minutes) {
        int total = ((int) minutes) % (24 * 60);
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    /** Перетворює рядок «ГГ:ХХ» на хвилини від півночі. */
    public static int parse(String hhmm) {
        String[] parts = hhmm.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Невірний формат часу: " + hhmm);
        }
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
