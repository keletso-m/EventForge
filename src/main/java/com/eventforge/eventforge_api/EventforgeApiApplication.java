package com.eventforge.eventforge_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventforgeApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(EventforgeApiApplication.class, args);
	}
}