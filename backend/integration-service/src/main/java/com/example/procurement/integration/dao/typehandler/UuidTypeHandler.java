package com.example.procurement.integration.dao.typehandler;

import java.sql.*;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** PostgreSQL UUID 必须通过 setObject 绑定，不能按普通 varchar 处理。 */
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType jdbcType) throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }
    @Override
    public UUID getNullableResult(ResultSet rs, String column) throws SQLException { return rs.getObject(column, UUID.class); }
    @Override
    public UUID getNullableResult(ResultSet rs, int index) throws SQLException { return rs.getObject(index, UUID.class); }
    @Override
    public UUID getNullableResult(CallableStatement statement, int index) throws SQLException {
        Object value = statement.getObject(index);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
}
