package hu.imregergo.paymentservice.eventsystem.dto;

import lombok.Data;

import java.util.UUID;

@Data
public abstract class Event {
    private String id = UUID.randomUUID().toString();
}
