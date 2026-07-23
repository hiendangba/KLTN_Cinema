package com.cinema.film_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:filmdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.grpc.client.channels.cinema.address=localhost:9196",
        "spring.grpc.client.channels.showtime.address=localhost:9194",
        "spring.grpc.client.channels.review.address=localhost:9198"
})
class FilmServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
