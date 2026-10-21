package com.example.bulletinboard.support;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import com.example.bulletinboard.security.AccountPrincipal;
public class WithAccountFactory implements WithSecurityContextFactory<WithAccount> {
    @Autowired ApplicationContext context;
    public SecurityContext createSecurityContext(WithAccount annotation) {
        TestActors.CONTEXT.set(context);TestActors.prepareMock(context,annotation.roles());
        var authorities=Arrays.stream(annotation.roles()).map(role -> new SimpleGrantedAuthority("ROLE_"+role)).toList();
        var principal=new AccountPrincipal(TestActors.id(annotation.username()),"unused",true,true,authorities);
        SecurityContext security=SecurityContextHolder.createEmptyContext();
        security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,authorities));return security;
    }
}
