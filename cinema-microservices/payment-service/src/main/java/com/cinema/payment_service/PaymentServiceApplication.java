package com.cinema.payment_service;

import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.config.VietQrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.cinema.payment_service", "com.cinema.exception"})
@EnableConfigurationProperties({
	VietQrProperties.class,
	MomoGatewayProperties.class
})
@EnableScheduling
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}

}
