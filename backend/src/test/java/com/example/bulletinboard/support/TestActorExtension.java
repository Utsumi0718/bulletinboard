package com.example.bulletinboard.support;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
public class TestActorExtension implements BeforeEachCallback,AfterEachCallback {
    public void beforeEach(ExtensionContext context) { TestActors.CONTEXT.set(SpringExtension.getApplicationContext(context));TestActors.IDS.get().clear(); }
    public void afterEach(ExtensionContext context) { TestActors.CONTEXT.remove();TestActors.IDS.remove(); }
}
