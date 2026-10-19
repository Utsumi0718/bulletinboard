package com.example.bulletinboard.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.example.bulletinboard.scheduler.ContactRetentionScheduler;

class ContactRetentionSchedulerTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ContactRetentionScheduler.class)
                .withBean(ContactRetentionService.class,()->mock(ContactRetentionService.class));
    }
    @Test void defaultsToDisabledWithoutProperty() {
        runner().run(context->assertThat(context).doesNotHaveBean(ContactRetentionScheduler.class));
    }
    @Test void explicitEnableCreatesJobAndFailureDoesNotEscape() {
        runner().withPropertyValues("contact.retention.enabled=true").run(context->{
            assertThat(context).hasSingleBean(ContactRetentionScheduler.class);
            doThrow(new IllegalStateException("fake confidential contents")).when(context.getBean(ContactRetentionService.class)).deleteExpired();
            assertThatCode(()->context.getBean(ContactRetentionScheduler.class).deleteExpiredContacts()).doesNotThrowAnyException();
        });
    }
}
