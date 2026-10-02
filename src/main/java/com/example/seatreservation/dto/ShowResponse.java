package com.example.seatreservation.dto;
import java.util.*;
public record ShowResponse(UUID id,String name,long pricePaise,int totalSeats,long available,long held,long confirmed,List<SeatView> seats) {}
