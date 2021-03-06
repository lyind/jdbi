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
package org.jdbi.v3.core.argument;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;

import static org.jdbi.v3.core.generic.GenericTypes.findGenericParameter;
import static org.jdbi.v3.core.generic.GenericTypes.getErasedType;

/**
 * The BuiltInArgumentFactory provides instances of {@link Argument} for
 * many core Java types.  Generally you should not need to use this
 * class directly, but instead should bind your object with the
 * {@link SqlStatement} convenience methods.
 */
public class BuiltInArgumentFactory implements ArgumentFactory {
    // Care for the initialization order here, there's a fair number of statics.  Create the builders before the factory instance.

    private static final ArgBuilder<String> STR_BUILDER = v -> new BuiltInArgument<>(String.class, Types.VARCHAR, PreparedStatement::setString, v);
    private static final Map<Class<?>, ArgBuilder<?>> BUILDERS = createInternalBuilders();

    public static final ArgumentFactory INSTANCE = new BuiltInArgumentFactory();

    private static <T> void register(Map<Class<?>, ArgBuilder<?>> map, Class<T> klass, int type, StatementBinder<T> binder) {
        register(map, klass, v -> new BuiltInArgument<>(klass, type, binder, v));
    }

    private static <T> void register(Map<Class<?>, ArgBuilder<?>> map, Class<T> klass, ArgBuilder<T> builder) {
        map.put(klass, builder);
    }

    /** Create a binder which calls String.valueOf on its argument and then delegates to another binder. */
    private static <T> StatementBinder<T> stringifyValue(StatementBinder<String> real) {
        return (p, i, v) -> real.bind(p, i, String.valueOf(v));
    }

    private static Map<Class<?>, ArgBuilder<?>> createInternalBuilders() {
        final Map<Class<?>, ArgBuilder<?>> map = new IdentityHashMap<>();
        register(map, BigDecimal.class, Types.NUMERIC, PreparedStatement::setBigDecimal);
        register(map, Blob.class, Types.BLOB, PreparedStatement::setBlob);
        register(map, Boolean.class, Types.BOOLEAN, PreparedStatement::setBoolean);
        register(map, boolean.class, Types.BOOLEAN, PreparedStatement::setBoolean);
        register(map, Byte.class, Types.TINYINT, PreparedStatement::setByte);
        register(map, byte.class, Types.TINYINT, PreparedStatement::setByte);
        register(map, byte[].class, Types.VARBINARY, PreparedStatement::setBytes);
        register(map, Character.class, Types.CHAR, stringifyValue(PreparedStatement::setString));
        register(map, char.class, Types.CHAR, stringifyValue(PreparedStatement::setString));
        register(map, Clob.class, Types.CLOB, PreparedStatement::setClob);
        register(map, Double.class, Types.DOUBLE, PreparedStatement::setDouble);
        register(map, double.class, Types.DOUBLE, PreparedStatement::setDouble);
        register(map, Float.class, Types.FLOAT, PreparedStatement::setFloat);
        register(map, float.class, Types.FLOAT, PreparedStatement::setFloat);
        register(map, Inet4Address.class, Types.OTHER, (p, i, v) -> p.setString(i, v.getHostAddress()));
        register(map, Inet6Address.class, Types.OTHER, (p, i, v) -> p.setString(i, v.getHostAddress()));
        register(map, Integer.class, Types.INTEGER, PreparedStatement::setInt);
        register(map, int.class, Types.INTEGER, PreparedStatement::setInt);
        register(map, java.util.Date.class, Types.TIMESTAMP, (p, i, v) -> p.setTimestamp(i, new Timestamp(v.getTime())));
        register(map, Long.class, Types.INTEGER, PreparedStatement::setLong);
        register(map, long.class, Types.INTEGER, PreparedStatement::setLong);
        register(map, Short.class, Types.SMALLINT, PreparedStatement::setShort);
        register(map, short.class, Types.SMALLINT, PreparedStatement::setShort);
        register(map, java.sql.Date.class, Types.DATE, PreparedStatement::setDate);
        register(map, String.class, STR_BUILDER);
        register(map, Time.class, Types.TIME, PreparedStatement::setTime);
        register(map, Timestamp.class, Types.TIMESTAMP, PreparedStatement::setTimestamp);
        register(map, URL.class, Types.DATALINK, PreparedStatement::setURL);
        register(map, URI.class, Types.VARCHAR, stringifyValue(PreparedStatement::setString));
        register(map, UUID.class, Types.VARCHAR, PreparedStatement::setObject);

        register(map, Instant.class, Types.TIMESTAMP, (p, i, v) -> p.setTimestamp(i, Timestamp.from(v)));
        register(map, LocalDate.class, Types.DATE, (p, i, v) -> p.setDate(i, java.sql.Date.valueOf(v)));
        register(map, LocalDateTime.class, Types.TIMESTAMP, (p, i, v) -> p.setTimestamp(i, Timestamp.valueOf(v)));
        register(map, OffsetDateTime.class, Types.TIMESTAMP, (p, i, v) -> p.setTimestamp(i, Timestamp.from(v.toInstant())));
        register(map, ZonedDateTime.class, Types.TIMESTAMP, (p, i, v) -> p.setTimestamp(i, Timestamp.from(v.toInstant())));
        register(map, LocalTime.class, Types.TIME, (p, i, v) -> p.setTime(i, Time.valueOf(v)));

        register(map, OptionalInt.class, Types.INTEGER, (p, i, v) -> {
            if (v.isPresent()) {
                p.setInt(i, v.getAsInt());
            } else {
                p.setNull(i, Types.INTEGER);
            }
        });
        register(map, OptionalLong.class, Types.BIGINT, (p, i, v) -> {
            if (v.isPresent()) {
                p.setLong(i, v.getAsLong());
            } else {
                p.setNull(i, Types.BIGINT);
            }
        });
        register(map, OptionalDouble.class, Types.DOUBLE, (p, i, v) -> {
            if (v.isPresent()) {
                p.setDouble(i, v.getAsDouble());
            } else {
                p.setNull(i, Types.DOUBLE);
            }
        });

        return Collections.unmodifiableMap(map);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Argument> build(Type expectedType, Object value, ConfigRegistry config) {
        Class<?> expectedClass = getErasedType(expectedType);

        if (value != null && expectedClass == Object.class) {
            expectedClass = value.getClass();
        }

        @SuppressWarnings("rawtypes")
        ArgBuilder v = BUILDERS.get(expectedClass);

        if (v != null) {
            return Optional.of(v.build(value));
        }

        // Enums must be bound as VARCHAR.
        if (value instanceof Enum) {
            Enum<?> enumValue = (Enum<?>) value;
            return Optional.of(STR_BUILDER.build(enumValue.name()));
        }

        if (value instanceof Optional) {
            Object nestedValue = ((Optional<?>) value).orElse(null);
            Type nestedType = findOptionalType(expectedType, nestedValue);
            return config.get(Arguments.class).findFor(nestedType, nestedValue);
        }

        return value == null
                ? Optional.of(config.get(Arguments.class).getUntypedNullArgument())
                : Optional.empty();
    }

    private Type findOptionalType(Type wrapperType, Object nestedValue) {
        if (getErasedType(wrapperType).equals(Optional.class)) {
            Optional<Type> nestedType = findGenericParameter(wrapperType, Optional.class);
            if (nestedType.isPresent()) {
                return nestedType.get();
            }
        }
        return nestedValue == null ? Object.class : nestedValue.getClass();
    }

    @FunctionalInterface
    interface StatementBinder<T> {
        void bind(PreparedStatement p, int index, T value) throws SQLException;
    }

    @FunctionalInterface
    interface ArgBuilder<T> {
        Argument build(T value);
    }

    static final class BuiltInArgument<T> implements Argument {
        private final T value;
        private final Class<T> klass;
        private final int type;
        private final StatementBinder<T> binder;

        private BuiltInArgument(Class<T> klass, int type, StatementBinder<T> binder, T value) {
            this.binder = binder;
            this.klass = klass;
            this.type = type;
            this.value = value;
        }

        @Override
        public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
            if (value == null) {
                statement.setNull(position, type);
                return;
            }
            binder.bind(statement, position, value);
        }

        @Override
        public String toString() {
            if (klass.isArray()) {
                return String.format("{array of %s length %s}", klass.getComponentType(), Array.getLength(value));
            }
            return String.valueOf(value);
        }

        StatementBinder<T> getStatementBinder() {
            return binder;
        }
    }
}
