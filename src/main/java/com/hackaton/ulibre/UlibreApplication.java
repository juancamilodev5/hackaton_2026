package com.hackaton.ulibre;

import java.util.TimeZone;

import com.hackaton.ulibre.comun.ZonaHoraria;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UlibreApplication {

	public static void main(String[] args) {
		// Antes de levantar el contexto: el driver JDBC envía esta zona a PostgreSQL al conectar
		// y las columnas timestamp (sin zona) guardan hora local de Colombia.
		TimeZone.setDefault(TimeZone.getTimeZone(ZonaHoraria.ZONA));
		SpringApplication.run(UlibreApplication.class, args);
	}

}
