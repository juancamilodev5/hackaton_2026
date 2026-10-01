package com.hackaton.ulibre.auth;

import java.time.OffsetDateTime;

public record LoginResponse(String token, OffsetDateTime expiraEn, UsuarioAutenticado usuario) {
}
