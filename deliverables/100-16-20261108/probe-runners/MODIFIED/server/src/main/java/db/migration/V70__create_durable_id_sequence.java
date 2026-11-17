package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.util.ArrayList;

public class V70__create_durable_id_sequence extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        long watermark = 1L << 62;
        var columns = new ArrayList<String[]>();
        try (var statement = connection.prepareStatement(
            "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'bigint' AND COLUMN_KEY = 'PRI'");
             var rows = statement.executeQuery()) {
            while (rows.next()) columns.add(new String[]{rows.getString(1), rows.getString(2)});
        }
        // 升级时停用旧发号实例；扫描历史主键，避免时钟异常产生过的旧 ID 与新序列重叠。
        for (var column : columns) {
            String table = column[0].replace("`", "``");
            String name = column[1].replace("`", "``");
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT MAX(`" + name + "`) FROM `" + table + "`")) {
                if (rows.next()) watermark = Math.max(watermark, rows.getLong(1));
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(32) NOT NULL PRIMARY KEY, "
                + "high_watermark BIGINT NOT NULL) ENGINE=InnoDB");
        }
        try (var statement = connection.prepareStatement("INSERT INTO sys_id_sequence VALUES ('global', ?)")) {
            statement.setLong(1, watermark);
            statement.executeUpdate();
        }
    }
}
