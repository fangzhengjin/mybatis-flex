/*
 *  Copyright (c) 2022-2024, Mybatis-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.mybatisflex.core.query;

import com.mybatisflex.core.dialect.IDialect;
import com.mybatisflex.core.util.ObjectUtil;
import com.mybatisflex.core.util.StringUtil;

import java.util.List;

/**
 * 子查询列。
 *
 * @author michael
 * @author 王帅
 */
public class SelectQueryColumn extends QueryColumn implements HasParamsColumn {

    private QueryWrapper queryWrapper;

    public SelectQueryColumn(QueryWrapper queryWrapper) {
        this.queryWrapper = queryWrapper;
    }

    public QueryWrapper getQueryWrapper() {
        return queryWrapper;
    }

    @Override
    protected String toConditionSql(List<QueryTable> queryTables, IDialect dialect) {
        return WrapperUtil.withBracket(dialect.forSelectByQuery(queryWrapper));
    }

    @Override
    protected String toSelectSql(List<QueryTable> queryTables, IDialect dialect) {
        String selectSql = dialect.forSelectByQuery(queryWrapper);
        if (StringUtil.hasText(selectSql) && StringUtil.hasText(alias)) {
            selectSql = WrapperUtil.withAlias(selectSql, alias, dialect);
        }
        return selectSql;
    }

    @Override
    public SelectQueryColumn clone() {
        SelectQueryColumn clone = (SelectQueryColumn) super.clone();
        // deep clone ...
        clone.queryWrapper = ObjectUtil.clone(this.queryWrapper);
        return clone;
    }

    @Override
    public Object[] getParamValues() {
        return queryWrapper.getAllValueArray();
    }

}
