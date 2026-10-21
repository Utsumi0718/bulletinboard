package com.example.bulletinboard.support;
import java.lang.annotation.*;
import org.springframework.security.test.context.support.*;
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.TYPE})
@WithSecurityContext(factory=WithAccountFactory.class,setupBefore=TestExecutionEvent.TEST_EXECUTION)
public @interface WithAccount {
    String username() default "1";
    String[] roles() default {"USER"};
}
