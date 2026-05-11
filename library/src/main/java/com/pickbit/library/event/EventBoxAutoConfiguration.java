package com.pickbit.library.event;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackageClasses = EventBoxIdCreateService.class)
public class EventBoxAutoConfiguration {
}
