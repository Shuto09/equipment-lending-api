package com.example.lending;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EquipmentLendingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(EquipmentLendingApiApplication.class, args);
	}

}
