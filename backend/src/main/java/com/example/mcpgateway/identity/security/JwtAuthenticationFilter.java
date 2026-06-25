package com.example.mcpgateway.identity.security;

import com.example.mcpgateway.identity.domain.repository.UserRepository;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService tokens;
    private final UserRepository users;
    public JwtAuthenticationFilter(JwtTokenService tokens,UserRepository users){this.tokens=tokens;this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                                               FilterChain chain)throws ServletException,IOException{
        String header=request.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")){
            try{
                var principal=tokens.parse(header.substring(7));
                var user=users.findById(principal.userId()).filter(u->u.isActive()).orElse(null);
                if(user!=null){
                    var auth=new UsernamePasswordAuthenticationToken(principal,null,
                            List.of(new SimpleGrantedAuthority("ROLE_"+user.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }catch(Exception ignored){ SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(request,response);
    }
}
