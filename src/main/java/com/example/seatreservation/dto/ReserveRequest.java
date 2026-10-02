package com.example.seatreservation.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record ReserveRequest(@NotEmpty List<@NotBlank String> seats,@NotBlank String idempotencyKey) {}
