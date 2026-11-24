package kitchen;

/** Страва з меню: назва та час приготування у віртуальних хвилинах. */
public class Dish {

    private final String name;
    private final int cookingMinutes;

    public Dish(String name, int cookingMinutes) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Назва страви не може бути порожньою");
        }
        if (cookingMinutes <= 0) {
            throw new IllegalArgumentException("Час приготування має бути додатним");
        }
        this.name = name;
        this.cookingMinutes = cookingMinutes;
    }

    public String getName() {
        return name;
    }

    public int getCookingMinutes() {
        return cookingMinutes;
    }

    @Override
    public String toString() {
        return name;
    }
}
