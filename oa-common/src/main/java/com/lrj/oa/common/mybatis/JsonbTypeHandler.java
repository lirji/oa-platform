package com.lrj.oa.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 把 Java 侧的 JSON 字符串写进 PostgreSQL 的 {@code jsonb} 列。
 *
 * <p>PG <b>不会</b>把 varchar 隐式转成 jsonb，直接绑字符串会报
 * {@code column "x" is of type jsonb but expression is of type character varying}。
 *
 * <p>另一种常见做法是在 JDBC URL 上加 {@code ?stringtype=unspecified} 让 PG 自己推断，
 * 但那会影响<b>所有</b>字符串参数的类型推断，等于用全局的类型宽松换局部的方便。
 * 这里用 TypeHandler 只作用在真正是 jsonb 的字段上，代价是每个字段要显式标注 ——
 * 显式是对的，因为"这一列是 jsonb"本来就是需要被看见的事实。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("jsonb");
        obj.setValue(parameter);
        ps.setObject(i, obj);
    }

    @Override public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }
    @Override public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }
    @Override public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
