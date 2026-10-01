package com.hackaton.ulibre.comun;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj único del backend. Todo "ahora" se obtiene con {@code LocalDateTime.now(clock)};
 * nunca {@code LocalDateTime.now()} sin reloj.
 */
@Configuration
public class RelojConfig {

    /**
     * Avanza de a microsegundos, la precisión de timestamp en PostgreSQL: lo que la API responde
     * justo después de guardar coincide con lo que queda en la base (en Windows el reloj da 100 ns).
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.system(ZonaHoraria.ZONA), Duration.ofNanos(1_000));
    }
}
