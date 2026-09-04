package me.psikuvit.betterWarden.core.event;

import java.util.function.Consumer;

/** Internal pub/sub for domain events. Not Spring's ApplicationEventPublisher (docs/spec/01-CORE.txt §10). */
public interface EventBus {

    <T> void subscribe(Class<T> eventType, Consumer<T> listener);

    void publish(Object event);
}
