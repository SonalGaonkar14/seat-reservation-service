package com.example.seatreservation.controller;

import com.example.seatreservation.dto.*;
import com.example.seatreservation.security.Auth;
import com.example.seatreservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/shows")
public class ShowController {
    private final ReservationService service;

    public ShowController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest req, HttpServletRequest r) {
        Auth.admin(r);
        return ResponseEntity.status(201).body(service.createShow(req));
    }

    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable UUID id) {
        return service.getShow(id);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<ReservationResponse> reserve(@PathVariable UUID id, @Valid @RequestBody ReserveRequest req, HttpServletRequest r) {
        return ResponseEntity.status(201).body(service.reserve(id, Auth.user(r), req));
    }
}
