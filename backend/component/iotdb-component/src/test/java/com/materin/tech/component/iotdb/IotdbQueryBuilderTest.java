package com.materin.tech.component.iotdb;

import com.materin.tech.component.timeseries.TimeSeriesQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IoTDB 查询 SQL 构造测试。 */
class IotdbQueryBuilderTest {

    private static final String DEV = "root.materin.p1.d3";

    @Test
    void rawQuery_shouldBuildRangeQuery() {
        var q = TimeSeriesQuery.builder("3").start(1000L).end(2000L)
                .keys(List.of("temperature")).limit(500).desc(true).build();
        String sql = IotdbQueryBuilder.rawQuery(DEV, q);
        assertThat(sql).isEqualTo(
                "SELECT temperature FROM root.materin.p1.d3 "
                        + "WHERE time >= 1000 AND time <= 2000 ORDER BY time DESC LIMIT 500");
    }

    @Test
    void rawQuery_shouldDefaultSelectAllWhenNoKeys() {
        var q = TimeSeriesQuery.builder("3").build();
        assertThat(IotdbQueryBuilder.rawQuery(DEV, q))
                .isEqualTo("SELECT * FROM root.materin.p1.d3 LIMIT 1000");
    }

    @Test
    void aggregationQuery_shouldGroupByInterval() {
        var q = TimeSeriesQuery.builder("3").start(1000L).end(2000L)
                .keys(List.of("temperature", "humidity"))
                .aggregation("avg", 30000L).build();
        String sql = IotdbQueryBuilder.aggregationQuery(DEV, q);
        assertThat(sql).isEqualTo("SELECT avg(temperature), avg(humidity) FROM root.materin.p1.d3 "
                + "WHERE time >= 1000 AND time <= 2000 GROUP BY([1000, 2000), 30s)");
    }

    @Test
    void aggregationQuery_shouldRejectUnknownFunction() {
        var q = TimeSeriesQuery.builder("3").keys(List.of("temperature"))
                .aggregation("drop database", 1000L).build();
        assertThatThrownBy(() -> IotdbQueryBuilder.aggregationQuery(DEV, q))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aggregationQuery_shouldClampDefaultStartToWindowBudget() {
        // start 未指定：只回看 MAX_AGG_WINDOWS(10000) 个窗口，而不是从 epoch 起物化
        long interval = 60_000L;
        long end = 1_700_000_000_000L;
        var q = TimeSeriesQuery.builder("3").end(end)
                .keys(List.of("temperature")).aggregation("avg", interval).build();
        String sql = IotdbQueryBuilder.aggregationQuery(DEV, q);
        long expectedStart = end - interval * 10_000L;
        assertThat(sql).isEqualTo("SELECT avg(temperature) FROM root.materin.p1.d3 "
                + "WHERE time <= " + end + " GROUP BY([" + expectedStart + ", " + end + "), 1m)");
    }

    @Test
    void aggregationQuery_shouldRejectExcessiveWindows() {
        // 显式超宽范围 × 小窗口：拒绝而非 OOM
        var q = TimeSeriesQuery.builder("3").start(0L).end(1_000_000_000_000L)
                .keys(List.of("temperature")).aggregation("count", 1000L).build();
        assertThatThrownBy(() -> IotdbQueryBuilder.aggregationQuery(DEV, q))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("聚合窗口数超过上限");
    }

    @Test
    void durationLiteral_shouldPreferLargestUnit() {
        assertThat(IotdbQueryBuilder.durationLiteral(30000L)).isEqualTo("30s");
        assertThat(IotdbQueryBuilder.durationLiteral(60000L)).isEqualTo("1m");
        assertThat(IotdbQueryBuilder.durationLiteral(3600000L)).isEqualTo("1h");
        assertThat(IotdbQueryBuilder.durationLiteral(86400000L)).isEqualTo("1d");
        assertThat(IotdbQueryBuilder.durationLiteral(500L)).isEqualTo("500ms");
    }
}
