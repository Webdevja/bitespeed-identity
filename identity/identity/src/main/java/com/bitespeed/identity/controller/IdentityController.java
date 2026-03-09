package com.bitespeed.identity.controller;

import com.bitespeed.identity.dto.ContactRequest;
import com.bitespeed.identity.dto.ContactResponse;
import com.bitespeed.identity.service.IdentityService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/identify")
public class IdentityController {

    private final IdentityService service;

    public IdentityController(IdentityService service) {
        this.service = service;
    }

    @PostMapping
    public ContactResponse identify(@RequestBody ContactRequest request) {

        return service.identify(
                request.getEmail(),
                request.getPhoneNumber()
        );
    }
}