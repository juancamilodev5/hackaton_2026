package com.hackaton.ulibre.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final AccesoConsultas accesos;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwt;
    private final Clock clock;
    /** Se compara contra él cuando el correo no existe, para que el tiempo de respuesta no lo delate. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, AccesoConsultas accesos, PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder, JwtProperties jwt, Clock clock) {
        this.usuarios = usuarios;
        this.accesos = accesos;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwt = jwt;
        this.clock = clock;
        this.hashFicticio = passwordEncoder.encode("hash-ficticio-para-tiempo-constante");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest solicitud) {
        Optional<Usuario> encontrado = usuarios.findByCorreoIgnoreCase(solicitud.correo().strip());
        String hash = encontrado.map(Usuario::getHashContrasena).orElse(null);
        boolean coincide = passwordEncoder.matches(solicitud.contrasena(), hash == null ? hashFicticio : hash);
        if (hash == null || !coincide || encontrado.get().getEstado() != EstadoUsuario.ACTIVO) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = encontrado.get();
        Acceso acceso = accesos.de(usuario.getId());
        Instant ahora = clock.instant();
        Instant expira = ahora.plus(jwt.expiracion());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtProperties.EMISOR)
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("correo", usuario.getCorreo())
                .claim("roles", acceso.roles())
                .claim(SecurityConfig.CLAIM_PERMISOS, acceso.permisos())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();

        return new LoginResponse(token, OffsetDateTime.ofInstant(expira, clock.getZone()),
                UsuarioAutenticado.de(usuario, acceso));
    }

    /** Datos frescos de la base: roles y permisos pueden haber cambiado desde que se emitió el token. */
    @Transactional(readOnly = true)
    public UsuarioAutenticado usuarioActual(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(u -> u.getEstado() == EstadoUsuario.ACTIVO)
                .orElseThrow(CredencialesInvalidasException::new);
        return UsuarioAutenticado.de(usuario, accesos.de(usuarioId));
    }
}
