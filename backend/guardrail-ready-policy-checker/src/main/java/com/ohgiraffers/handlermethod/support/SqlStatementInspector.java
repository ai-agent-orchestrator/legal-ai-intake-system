package com.ohgiraffers.handlermethod.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

public class SqlStatementInspector implements StatementInspector {

    @Override
    public String inspect(String sql) {
        TraceContext.incrementSqlStatementCount();
        return sql;
    }
}
