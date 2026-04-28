package com.pickbit.library.event;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EventBoxIdCreateService {

    public String createEventId(String serviceName) {
        return serviceName + "-" + UUID.randomUUID();
    }
}