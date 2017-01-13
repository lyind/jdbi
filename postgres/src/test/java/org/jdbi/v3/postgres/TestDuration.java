/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.postgres;

import com.google.common.collect.ImmutableList;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDuration {
    @ClassRule
    public static PostgresDbRule postgresDbRule = new PostgresDbRule();

    private Handle handle;

    private final Duration testDuration = Duration.ofDays(39).plusHours(23).plusMinutes(59).plusSeconds(1);

    @Before
    public void setUp() throws Exception {
        handle = IntervalTestCommon.setUp(postgresDbRule);
    }

    @Test
    public void testReadsViaFluentAPI() {
        List<Duration> periods = handle.createQuery("select foo from intervals where id = 1 or id = 2 order by id")
                .mapTo(Duration.class)
                .list();

        assertThat(periods).isEqualTo(ImmutableList.of(
                Duration.ofDays(1).plusHours(15),
                Duration.ofDays(40).plusMinutes(22)
        ));
    }

    @Test
    public void testTrivialDuration() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",6, Duration.ZERO);
        Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 6)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d.isZero());
    }

    @Test
    public void testHandlesNulls() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",7, null);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 7)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d).isNull();
    }

    @Test
    public void testWritesViaFluentApi() {
        handle.insert("insert into intervals(id, foo) values(?, ?)", 8, testDuration);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 8)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d).isEqualTo(testDuration);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDuration() {
        handle.createQuery("select foo from intervals where id=?")
                .bind(0, 5)
                .mapTo(Duration.class)
                .findOnly();
    }

    @Test
    public void testReadNegativeDuration() {
        handle.insert("insert into intervals(id, foo) values(?, interval '-2 days -3 hours')", 9);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 9)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d).isEqualTo(Duration.ofDays(-2).plusHours(-3));
    }

    @Test
    public void testWriteReadNegativeDuration() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",
                10, Duration.ofDays(-3).plusMinutes(2));
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 10)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d).isEqualTo(Duration.ofDays(-3).plusMinutes(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteDurationTooBig() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",
                11, Duration.ofDays((long)Integer.MAX_VALUE + 1));
    }

    /**
     * This is admittedly a test of an implementation detail, but this detail is documented in
     * {@link DurationArgumentFactory}, so this test failing is a good signal to update the documentation there.
     */
    @Test(expected = ArithmeticException.class)
    public void testWriteDurationTooSmall() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",
                12, Duration.ofSeconds(Long.MIN_VALUE));
    }

    @Test
    public void testTinyDuration() {
        handle.insert("insert into intervals(id, foo) values(?, interval '13us')", 13);
        final Duration d = handle.createQuery("select foo from intervals where id=?")
                .bind(0, 13)
                .mapTo(Duration.class)
                .findOnly();
        assertThat(d).isEqualTo(Duration.ofNanos(13_000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDurationTooPrecise() {
        handle.insert("insert into intervals(id, foo) values(?, ?)",
                14, Duration.ofNanos(100));
    }

    // We guard against reading intervals with seconds too big or too small (i.e., more extreme than Long minimum and
    // maximum values), but it's unclear how to actually create such intervals in Postgres.
}
