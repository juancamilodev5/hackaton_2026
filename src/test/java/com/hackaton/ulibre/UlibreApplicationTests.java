package com.hackaton.ulibre;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Arranque completo: Flyway sobre PostgreSQL 18 real y Hibernate validate de todas las entidades. */
@SpringBootTest
@ActiveProfiles("dev")
@Import(PostgresDePrueba.class)
class UlibreApplicationTests {

	@Test
	void contextLoads() {
	}

}
