package com.hackaton.ulibre.checklist;

import jakarta.validation.constraints.Size;

public record CierreFaseRequest(@Size(max = 4000) String notas) {
}
