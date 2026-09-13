package com.mediaflow.archiver.event;

import org.springframework.context.ApplicationEvent;

/**
 * The internal JVM Buzzer. 
 * Allows the fast Staging/Filer engines to instantly wake the slow AI Thread 
 * without causing complex multi-threading spaghetti code.
 */
public class AiInferenceWakeEvent extends ApplicationEvent {
    public AiInferenceWakeEvent(Object source) {
        super(source);
    }
}
