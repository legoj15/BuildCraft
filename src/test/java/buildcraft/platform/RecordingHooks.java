/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * A stand-in for a hook interface ({@link buildcraft.platform.event.GameHooks},
 * {@link buildcraft.platform.event.LifecycleHooks}) that registers nothing and records every call, for tests that pin
 * which hooks a class's {@code registerHooks} wires and then run the listeners it handed over.
 */
public final class RecordingHooks<T> {

    /** One registration: the hook method's name and its arguments (the listener last). */
    public record Call(String method, Object[] args) {}

    private final List<Call> calls = new ArrayList<>();
    private final T hooks;

    private RecordingHooks(Class<T> type) {
        this.hooks = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "RecordingHooks(" + type.getSimpleName() + ")";
                        };
                    }
                    calls.add(new Call(method.getName(), args == null ? new Object[0] : args.clone()));
                    return null;
                }));
    }

    public static <T> RecordingHooks<T> of(Class<T> type) {
        return new RecordingHooks<>(type);
    }

    /** The recording stand-in to pass to {@code registerHooks}. */
    public T hooks() {
        return hooks;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /** How many listeners were registered on {@code method}. */
    public long count(String method) {
        return calls.stream().filter(call -> call.method().equals(method)).count();
    }

    /** The single call to {@code method}; fails the test unless there is exactly one. */
    public Call only(String method) {
        List<Call> matching = calls.stream().filter(call -> call.method().equals(method)).toList();
        Assertions.assertEquals(1, matching.size(), "expected one " + method + " registration, got " + calls);
        return matching.get(0);
    }

    /** The listener (last argument) of the single call to {@code method}, as {@code type}. */
    public <L> L listener(String method, Class<L> type) {
        Object[] args = only(method).args();
        return type.cast(args[args.length - 1]);
    }
}
