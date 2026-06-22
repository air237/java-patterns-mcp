package com.javapatterns.examples.builder;

/**
 * Builder pattern — fluent step-by-step construction of an immutable
 * {@code Car} value object.
 *
 * <p>Builder shines when the target object has many optional or
 * mutually-dependent fields and a telescoping constructor (or setter soup)
 * would be unreadable. {@link Car} below has a final field for every
 * configurable attribute and is constructed only through {@link Car.Builder}.</p>
 */
public final class Car {

    private final String make;
    private final String model;
    private final int seats;
    private final boolean gps;
    private final String tripComputer;

    private Car(Builder b) {
        this.make = b.make;
        this.model = b.model;
        this.seats = b.seats;
        this.gps = b.gps;
        this.tripComputer = b.tripComputer;
    }

    public String make()         { return make; }
    public String model()        { return model; }
    public int seats()           { return seats; }
    public boolean hasGps()      { return gps; }
    public String tripComputer() { return tripComputer; }

    public static Builder builder(String make, String model) {
        return new Builder(make, model);
    }

    /** Static nested Builder — the fluent API for constructing a {@link Car}. */
    public static final class Builder {
        private final String make;
        private final String model;
        private int seats = 4;
        private boolean gps = false;
        private String tripComputer = "off";

        private Builder(String make, String model) {
            if (make == null || make.isBlank()) {
                throw new IllegalArgumentException("make must not be blank");
            }
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("model must not be blank");
            }
            this.make = make;
            this.model = model;
        }

        public Builder seats(int seats)             { this.seats = seats; return this; }
        public Builder gps(boolean enabled)         { this.gps = enabled; return this; }
        public Builder tripComputer(String mode)    { this.tripComputer = mode; return this; }

        public Car build() {
            if (seats < 1 || seats > 9) {
                throw new IllegalStateException("seats must be 1..9, got " + seats);
            }
            return new Car(this);
        }
    }
}
