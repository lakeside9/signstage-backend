package com.eformworks.signstage.backend.core.web;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnabledRequest {
    @NotNull
    private Boolean enabled;
}
