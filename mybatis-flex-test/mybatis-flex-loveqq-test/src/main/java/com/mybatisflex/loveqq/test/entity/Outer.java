/*
 *  Copyright (c) 2022-2025, Mybatis-Flex (fuhai999@gmail.com).
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

package com.mybatisflex.loveqq.test.entity;

import com.mybatisflex.annotation.ColumnAlias;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.loveqq.test.model.IdEntity;

/**
 * @author 王帅
 * @since 2023-06-16
 */
@Table("tb_outer")
public class Outer extends IdEntity<Integer> {

    private String name;
    private Inner inner;

    @Override
    @ColumnAlias("outer_id")
    public Integer getId() {
        return super.getId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner inner) {
        this.inner = inner;
    }

    @Override
    public String toString() {
        return "Outer{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", inner=" + inner +
            '}';
    }

}
