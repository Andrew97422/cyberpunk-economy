package ru.andrew.mainserver.pin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePinRequest {

    @NotBlank
    private String publicName;

    @NotBlank
    private String rawPin;

    @NotNull
    @Min(1)
    @Max(1440)
    private Integer durationMinutes;

    private String comment;
}