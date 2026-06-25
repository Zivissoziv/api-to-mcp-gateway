package com.example.mcpgateway.identity.infrastructure.persistence;

import com.example.mcpgateway.identity.domain.model.User;
import com.example.mcpgateway.identity.domain.model.UserRole;
import com.example.mcpgateway.identity.domain.model.UserStatus;
import com.example.mcpgateway.identity.domain.repository.UserRepository;
import com.example.mcpgateway.identity.infrastructure.security.BcryptPasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final BcryptPasswordService passwords;
    private final String username;
    private final String password;
    public BootstrapAdminInitializer(UserRepository users,BcryptPasswordService passwords,
            @Value("${app.security.bootstrap-admin.username}")String username,
            @Value("${app.security.bootstrap-admin.password}")String password){
        this.users=users;this.passwords=passwords;this.username=username;this.password=password;
    }
    @Override public void run(ApplicationArguments args){
        if(users.count()==0){
            Instant now=Instant.now();
            users.save(new User(UUID.randomUUID().toString(),username,
                    passwords.encode(password),UserRole.SYSTEM_ADMIN,UserStatus.ACTIVE,now,now));
        }
    }
}
