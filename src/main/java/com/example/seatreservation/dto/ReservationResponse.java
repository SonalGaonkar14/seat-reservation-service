package com.example.seatreservation.dto;
import java.util.*;
public record ReservationResponse(UUID reservationId,UUID showId,String userId,List<String> seats,long amountPaise,String status) {}
