package com.javapatterns.examples.decorator;

public final class SmsDecorator extends NotifierDecorator {

    private final String phoneNumber;

    public SmsDecorator(Notifier wrapped, String phoneNumber) {
        super(wrapped);
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String send(String message) {
        return super.send(message) + " | [sms -> " + phoneNumber + "]: " + message;
    }
}
