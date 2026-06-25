package com.example.mcpgateway.identity.controller;

import com.example.mcpgateway.identity.application.service.UserManagementService;
import com.example.mcpgateway.identity.domain.model.UserRole;
import com.example.mcpgateway.identity.domain.model.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserManagementService users;
    public UserController(UserManagementService users){this.users=users;}

    @GetMapping
    List<UserManagementService.UserView> list(){return users.listUsers();}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserManagementService.UserView create(@Valid @RequestBody CreateRequest request){
        return users.createUser(request.username(),request.password(),request.role());
    }

    @PatchMapping("/{userId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeStatus(@PathVariable String userId,@Valid @RequestBody StatusRequest request){
        users.changeStatus(userId,request.status());
    }

    public record CreateRequest(@NotBlank String username,@NotBlank String password,@NotNull UserRole role){}
    public record StatusRequest(@NotNull UserStatus status){}
}
