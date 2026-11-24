package kitchen;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Варіант 8: «Обробка замовлень на кухні».
 *
 * Сценарій: кухня ресторану працює з 10:00 до 16:00. Є 6 кухарів, але лише 4 плити
 * (семафор). Клієнти приходять у різний час — зокрема до відкриття та після закриття.
 * Вдень час «прискорений»: 20 хвилин віртуального часу = 1 реальна секунда.
 */
public class Main {

    // Налаштування симуляції
    private static final int STOVES = 4;
    private static final String OPEN = "10:00";
    private static final String CLOSE = "16:00";
    private static final String SIMULATION_START = "09:40";
    private static final double MINUTES_PER_SECOND = 20;

    public static void main(String[] args) {
        setUtf8Output();
        try {
            runSimulation();
        } catch (Exception e) {
            System.err.println("Симуляція завершилась з помилкою: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSimulation() throws InterruptedException {
        SimClock clock = new SimClock(SimClock.parse(SIMULATION_START), MINUTES_PER_SECOND);
        KitchenLog log = new KitchenLog(clock);
        Kitchen kitchen = new Kitchen(clock, log, STOVES, SimClock.parse(OPEN), SimClock.parse(CLOSE));

        System.out.println("==================================================");
        System.out.println(" СИМУЛЯЦІЯ РОБОТИ КУХНІ РЕСТОРАНУ");
        System.out.println(" Робочі години кухні: " + OPEN + " – " + CLOSE);
        System.out.println(" Плит на кухні: " + STOVES + ", кухарів: 6");
        System.out.println("==================================================");

        // Якщо в якомусь потоці станеться неочікувана помилка — покажемо її, а не втратимо
        Thread.UncaughtExceptionHandler handler = (thread, error) ->
                System.err.println("!!! Несподівана помилка в потоці «" + thread.getName() + "»: " + error);

        // ---- Кухарі ----
        String[] cookNames = {"Іван", "Марія", "Петро", "Олена", "Андрій", "Софія"};
        Map<Thread, Cook> cooks = new LinkedHashMap<>();
        for (String cookName : cookNames) {
            Cook cook = new Cook(cookName, kitchen);
            Thread thread = new Thread(cook, "Кухар " + cookName); // Runnable передаємо в Thread
            thread.setUncaughtExceptionHandler(handler);
            cooks.put(thread, cook);
        }

        // ---- Меню ----
        Dish borscht = new Dish("Борщ", 30);
        Dish pizza = new Dish("Піца", 40);
        Dish pasta = new Dish("Паста", 25);
        Dish steak = new Dish("Стейк", 60);
        Dish salad = new Dish("Салат", 20);
        Dish salmon = new Dish("Лосось", 45);
        Dish risotto = new Dish("Різото", 35);
        Dish soup = new Dish("Крем-суп", 25);

        // ---- Клієнти (ім'я, страва, час приходу) ----
        List<Client> clients = new ArrayList<>();
        clients.add(new Client("Олег", salad, "09:50", kitchen));      // ще до відкриття
        clients.add(new Client("Анна", borscht, "10:02", kitchen));
        clients.add(new Client("Богдан", pizza, "10:05", kitchen));
        clients.add(new Client("Віктор", steak, "10:08", kitchen));
        clients.add(new Client("Галина", pasta, "10:10", kitchen));
        clients.add(new Client("Денис", salmon, "10:12", kitchen));
        clients.add(new Client("Єва", risotto, "10:15", kitchen));
        clients.add(new Client("Жанна", soup, "10:18", kitchen));
        clients.add(new Client("Захар", borscht, "11:30", kitchen));
        clients.add(new Client("Ірина", pizza, "12:30", kitchen));
        clients.add(new Client("Йосип", salad, "13:40", kitchen));
        clients.add(new Client("Катерина", pasta, "14:50", kitchen));
        // Велика черга перед закриттям — частину замовлень не встигнуть узяти в роботу
        clients.add(new Client("Лев", steak, "15:40", kitchen));
        clients.add(new Client("Марта", salmon, "15:42", kitchen));
        clients.add(new Client("Назар", risotto, "15:44", kitchen));
        clients.add(new Client("Орест", pizza, "15:46", kitchen));
        clients.add(new Client("Поліна", steak, "15:48", kitchen));
        clients.add(new Client("Руслан", salmon, "15:50", kitchen));
        clients.add(new Client("Соломія", steak, "15:52", kitchen));
        clients.add(new Client("Тарас", pizza, "15:54", kitchen));
        clients.add(new Client("Уляна", risotto, "15:56", kitchen));
        clients.add(new Client("Федір", borscht, "15:58", kitchen));
        clients.add(new Client("Христина", pasta, "16:10", kitchen));  // вже після закриття
        clients.add(new Client("Юрій", salad, "16:40", kitchen));      // вже після закриття

        List<Thread> clientThreads = new ArrayList<>();
        for (Client client : clients) {
            Thread thread = new Thread(client, "Клієнт " + client.getName());
            thread.setUncaughtExceptionHandler(handler);
            clientThreads.add(thread);
        }

        // ---- Монітор станів потоків (щоб показати Thread states) ----
        ThreadStateMonitor monitorTask = new ThreadStateMonitor(cooks, log, 2000);
        Thread monitor = new Thread(monitorTask, "Монітор");
        monitor.setDaemon(true); // не заважає завершенню програми

        // Стан NEW: потоки створені, але ще не запущені
        monitorTask.printStates("Кухарі створені, але ще не запущені");

        // ---- Запуск ----
        for (Thread t : cooks.keySet()) {
            t.start();
        }
        for (Thread t : clientThreads) {
            t.start();
        }
        monitor.start();

        // Головний потік — «менеджер»: чекає закриття кухні й скасовує те, що не встигли взяти в роботу
        clock.sleepUntil(kitchen.getCloseAt());
        log.info("Кухня ЗАЧИНЯЄТЬСЯ (" + CLOSE + "). Нові замовлення не приймаються");
        kitchen.closeAndCancelPending();

        // Чекаємо, поки всі потоки завершаться (метод join)
        for (Thread t : clientThreads) {
            t.join();
        }
        for (Thread t : cooks.keySet()) {
            t.join();
        }
        monitor.interrupt();
        monitor.join();

        // Стан TERMINATED: усі потоки завершили роботу
        monitorTask.printStates("Робочий день закінчено");

        System.out.println();
        System.out.println("================ ПІДСУМКИ ДНЯ ================");
        System.out.println(kitchen.summary());
        System.out.println("==============================================");
    }

    /** Щоб українські літери коректно виводились у будь-якому терміналі. */
    private static void setUtf8Output() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8"));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 підтримується завжди; якщо ні — залишаємо стандартне кодування
            System.err.println("Не вдалося увімкнути UTF-8: " + e.getMessage());
        }
    }
}
