package com.workforcehub.common.util;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Only standalone unit tests receive a private fixture; Spring tests must wire the production bean. */
public class StandaloneIdGeneratorExtension implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        boolean spring = AnnotationSupport.findRepeatableAnnotations(context.getRequiredTestClass(), ExtendWith.class)
            .stream().flatMap(annotation -> java.util.Arrays.stream(annotation.value()))
            .anyMatch(SpringExtension.class::equals);
        if (spring || context.getTags().contains("durable-id")) return;
        var source = new DriverManagerDataSource("jdbc:h2:mem:ids_" + java.util.UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_id_sequence(sequence_name VARCHAR(32) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES('global', ?)", 1L << 62);
        var store = context.getStore(ExtensionContext.Namespace.create(getClass(), context.getUniqueId()));
        store.put("jdbc", jdbc);
        store.put("generator", new DatabaseIdGenerator(source));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var store = context.getStore(ExtensionContext.Namespace.create(getClass(), context.getUniqueId()));
        var generator = store.remove("generator", DatabaseIdGenerator.class);
        if (generator != null) {
            generator.close();
            store.remove("jdbc", JdbcTemplate.class).execute("SHUTDOWN");
        }
    }
}
