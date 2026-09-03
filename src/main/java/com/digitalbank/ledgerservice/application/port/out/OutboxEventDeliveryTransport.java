package com.digitalbank.ledgerservice.application.port.out;

public interface OutboxEventDeliveryTransport {

    void deliver(ClaimedOutboxEvent event);
}
