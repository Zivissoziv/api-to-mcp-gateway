package com.example.mcpgateway.identity.controller;

import com.example.mcpgateway.identity.application.service.AuthenticationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService authentication;
    public AuthController(AuthenticationService authentication){this.authentication=authentication;}

    @PostMapping("/login")
    AuthenticationService.TokenPair login(@Valid @RequestBody LoginRequest request){
        return authentication.login(request.username(),request.password());
    }
    @PostMapping("/refresh")
    AuthenticationService.TokenPair refresh(@Valid @RequestBody TokenRequest request){
        return authentication.refresh(request.refreshToken());
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody TokenRequest request){authentication.logout(request.refreshToken());}

    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record TokenRequest(@NotBlank String refreshToken){}
}
