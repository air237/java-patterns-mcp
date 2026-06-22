package com.javapatterns.examples.factory_method;

/** Product interface — what every concrete button must do. */
public interface Button {
    String onClick(String action);
}
