package com.workforcehub.common.util;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

public final class IdGeneratorProcessProbe {
    public static void main(String[] args) {
        // This intentionally uses production component discovery rather than the standalone JUnit fixture.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource(args[0], "root", ""));
            context.scan("com.workforcehub.common.util");
            context.refresh();
            int count = Integer.parseInt(args[1]);
            for (int index = 0; index < count; index++) System.out.println("ID=" + IdGenerator.nextId());
        }
    }
}
