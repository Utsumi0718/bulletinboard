package com.example.bulletinboard.support;

import java.util.*;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.mockito.Mockito;
import com.example.bulletinboard.repository.UserRepository;
import com.example.bulletinboard.model.User;
import com.example.bulletinboard.security.AccountPrincipal;

/** 本番のメール認証フォールバックを作らず、テスト側だけでID主体を構築する。 */
public final class TestActors {
    static final ThreadLocal<ApplicationContext> CONTEXT=new ThreadLocal<>();
    static final ThreadLocal<Map<String,Long>> IDS=ThreadLocal.withInitial(HashMap::new);
    private TestActors() { }
    public static long id(String alias) {
        if(alias.matches("[1-9][0-9]{0,18}")) return Long.parseLong(alias);
        ApplicationContext context=CONTEXT.get();
        if(context!=null) {
            var provider=context.getBeanProvider(UserRepository.class);var repository=provider.getIfAvailable();
            if(repository!=null && !Mockito.mockingDetails(repository).isMock()) {
                var user=repository.findByEmail(alias);
                if(user.isPresent()) {IDS.get().put(alias,user.get().getId());return user.get().getId();}
            }
        }
        return IDS.get().getOrDefault(alias,1L+Integer.toUnsignedLong(alias.hashCode()));
    }
    public static String key(String alias) { return Long.toString(id(alias)); }
    public static ActorRequest user(String alias) { return new ActorRequest(alias); }
    public static RequestPostProcessor user(UserDetails principal) {
        if (principal instanceof AccountPrincipal) return SecurityMockMvcRequestPostProcessors.user(principal);
        return user(principal.getUsername()).roles(principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).toArray(String[]::new));
    }
    public static class ActorRequest implements RequestPostProcessor {
        private final String alias;
        private String[] roles={"USER"};
        ActorRequest(String alias) {this.alias=alias;}
        public ActorRequest roles(String... roles) {this.roles=roles;return this;}
        public org.springframework.mock.web.MockHttpServletRequest postProcessRequest(org.springframework.mock.web.MockHttpServletRequest request) {
            ApplicationContext context=WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
            if(context!=null) CONTEXT.set(context);
            prepareMock(context,roles);
            long userId=id(alias);long version=0;
            if(context!=null) {
                var repo=context.getBeanProvider(UserRepository.class).getIfAvailable();
                if(repo!=null && !Mockito.mockingDetails(repo).isMock()) version=repo.findById(userId).map(User::getAuthenticationVersion).orElse(0L);
            }
            var authorities=Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_"+role)).toList();
            return SecurityMockMvcRequestPostProcessors.user(new AccountPrincipal(userId,"unused-test-value",true,true,authorities,version)).postProcessRequest(request);
        }
    }
    static void prepareMock(ApplicationContext context,String[] roles) {
        if(context==null)return;
        var repo=context.getBeanProvider(UserRepository.class).getIfAvailable();
        if(repo==null || !Mockito.mockingDetails(repo).isMock())return;
        if(Mockito.mockingDetails(repo).getStubbings().stream().anyMatch(stub -> stub.getInvocation().getMethod().getName().equals("findById")))return;
        // MVC単体テストの認証用DBを明示的に模擬する。実Repositoryや実ログインテストには適用しない。
        Mockito.when(repo.findById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(invocation -> {
            User user=new User();user.setId(invocation.getArgument(0));user.setUsername("test actor");
            user.setEmail("test-actor@example.test");user.setPassword("unused");user.setRole("ROLE_"+roles[0]);
            return Optional.of(user);
        });
    }
}
