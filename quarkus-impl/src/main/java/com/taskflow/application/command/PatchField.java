package com.taskflow.application.command;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Tri-state PATCH field: absent from the request body, present with an
 * explicit null (allowed by the spec for nullable fields like description),
 * or present with a value. java.util.Optional cannot express the middle
 * state, hence this type. The REST adapter builds it from JSON key presence.
 */
public final class PatchField<T> {

    private static final PatchField<?> ABSENT = new PatchField<>(false, null);

    private final boolean present;
    private final T value;

    private PatchField(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchField<T> absent() {
        return (PatchField<T>) ABSENT;
    }

    /** Present field; the value may be an explicit null. */
    public static <T> PatchField<T> ofNullable(T value) {
        return new PatchField<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    /** The submitted value (possibly null). Throws if the field was absent. */
    public T value() {
        if (!present) {
            throw new NoSuchElementException("field is absent from the patch");
        }
        return value;
    }

    /** Runs the action with the submitted value (possibly null) only if the field was present. */
    public void ifPresent(Consumer<T> action) {
        if (present) {
            action.accept(value);
        }
    }
}
