package com.hackaton.ulibre.auth;

import java.io.IOException;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Claim del token con los códigos de permiso; se usan tal cual (sin prefijo) como authorities. */
    public static final String CLAIM_PERMISOS = "permisos";

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http, JwtAuthenticationConverter convertidor)
            throws Exception {
        AuthenticationEntryPoint noAutenticado = noAutenticado();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(noAutenticado))
                .exceptionHandling(e -> e.authenticationEntryPoint(noAutenticado));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter permisos = new JwtGrantedAuthoritiesConverter();
        permisos.setAuthoritiesClaimName(CLAIM_PERMISOS);
        permisos.setAuthorityPrefix("");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(permisos);
        return convertidor;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties propiedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveHmac(propiedades)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties propiedades) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveHmac(propiedades))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtProperties.EMISOR));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties cors) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.origins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }

    private static SecretKey claveHmac(JwtProperties propiedades) {
        return new SecretKeySpec(propiedades.secretoEnBytes(), "HmacSHA256");
    }

    /** 401 con el encabezado WWW-Authenticate estándar y cuerpo problem+json. */
    private static AuthenticationEntryPoint noAutenticado() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            bearer.commence(request, response, ex);
            escribirProblema(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado",
                    "Se requiere un token válido");
        };
    }

    private static void escribirProblema(HttpServletResponse response, int estado, String titulo, String detalle)
            throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + estado
                + ",\"detail\":\"" + detalle + "\"}");
    }
}
