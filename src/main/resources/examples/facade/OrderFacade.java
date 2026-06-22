package com.javapatterns.examples.facade;

/**
 * Facade pattern — a thin layer that hides a messy subsystem behind a
 * simple, stable API.
 *
 * <p>In real life the "subsystem" is a video-decoding library, a
 * legacy banking API, a multi-step provisioning flow. Here we just sketch
 * three internal services and show how the {@code OrderFacade} keeps the
 * client out of them.</p>
 */
public final class OrderFacade {

    private final InventoryService inventory = new InventoryService();
    private final PaymentService payment     = new PaymentService();
    private final ShippingService shipping   = new ShippingService();

    /**
     * Single-call workflow that hides three internal steps from the caller.
     * Returns a one-line human-readable result for demonstration.
     */
    public String placeOrder(String sku, int quantity, String creditCard, String address) {
        boolean reserved = inventory.reserve(sku, quantity);
        if (!reserved) {
            return "FAILED: out of stock for " + sku;
        }
        String paymentId = payment.charge(creditCard, quantity * 9.99);
        String trackingNumber = shipping.dispatch(sku, quantity, address);
        return "OK: paid=" + paymentId + " tracking=" + trackingNumber;
    }

    /* ── internal subsystem (would be separate classes in a real app) ── */

    static final class InventoryService {
        boolean reserve(String sku, int quantity) {
            return !sku.equals("OUT_OF_STOCK") && quantity > 0;
        }
    }

    static final class PaymentService {
        String charge(String card, double amount) {
            return "PMT-" + Math.abs(card.hashCode() % 100_000);
        }
    }

    static final class ShippingService {
        String dispatch(String sku, int quantity, String address) {
            return "TRK-" + Math.abs((sku + address).hashCode() % 1_000_000);
        }
    }
}
