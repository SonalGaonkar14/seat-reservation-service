package com.example.seatreservation.controller;

import com.example.seatreservation.dto.CancelResponse;
import com.example.seatreservation.security.Auth;
import com.example.seatreservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/cancel")
    public CancelResponse cancel(@PathVariable UUID id, HttpServletRequest r) {
        return service.cancel(id, Auth.user(r));
    }
}
