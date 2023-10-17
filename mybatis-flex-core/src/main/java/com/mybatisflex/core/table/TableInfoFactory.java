/*
 *  Copyright (c) 2022-2023, Mybatis-Flex (fuhai999@gmail.com).
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
package com.mybatisflex.core.table;

import com.mybatisflex.annotation.*;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.exception.FlexExceptions;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.ClassUtil;
import com.mybatisflex.core.util.CollectionUtil;
import com.mybatisflex.core.util.Reflectors;
import com.mybatisflex.core.util.StringUtil;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.reflection.Reflector;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.*;
import org.apache.ibatis.util.MapUtil;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.chrono.JapaneseDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TableInfoFactory {

    private TableInfoFactory() {
    }

    static final Set<Class<?>> defaultSupportColumnTypes = CollectionUtil.newHashSet(
        int.class, Integer.class,
        short.class, Short.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class,
        boolean.class, Boolean.class,
        Date.class, java.sql.Date.class, Time.class, Timestamp.class,
        Instant.class, LocalDate.class, LocalDateTime.class, LocalTime.class, OffsetDateTime.class, OffsetTime.class, ZonedDateTime.class,
        Year.class, Month.class, YearMonth.class, JapaneseDate.class,
        byte[].class, Byte[].class, Byte.class,
        BigInteger.class, BigDecimal.class,
        char.class, String.class, Character.class
    );

    static final Set<Class<?>> ignoreColumnTypes = CollectionUtil.newHashSet(
        QueryWrapper.class, QueryColumn.class, QueryCondition.class, QueryChain.class
    );


    private static final Map<Class<?>, TableInfo> mapperTableInfoMap = new ConcurrentHashMap<>();
    private static final Map<Class<?>, TableInfo> entityTableMap = new ConcurrentHashMap<>();
    private static final Map<String, TableInfo> tableInfoMap = new ConcurrentHashMap<>();
    private static final Set<String> initedPackageNames = new HashSet<>();


    public synchronized static void init(String mapperPackageName) {
        if (!initedPackageNames.contains(mapperPackageName)) {
            ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
            resolverUtil.find(new ResolverUtil.IsA(BaseMapper.class), mapperPackageName);
            Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
            for (Class<? extends Class<?>> mapperClass : mapperSet) {
                ofMapperClass(mapperClass);
            }
            initedPackageNames.add(mapperPackageName);
        }
    }


    public static TableInfo ofMapperClass(Class<?> mapperClass) {
        return MapUtil.computeIfAbsent(mapperTableInfoMap, mapperClass, key -> {
            Class<?> entityClass = getEntityClass(mapperClass);
            if (entityClass == null) {
                return null;
            }
            return ofEntityClass(entityClass);
        });
    }


    public static TableInfo ofEntityClass(Class<?> entityClass) {
        return MapUtil.computeIfAbsent(entityTableMap, entityClass, aClass -> {
            TableInfo tableInfo = createTableInfo(entityClass);
            tableInfoMap.put(tableInfo.getTableNameWithSchema(), tableInfo);
            return tableInfo;
        });
    }


    public static TableInfo ofTableName(String tableName) {
        return StringUtil.isNotBlank(tableName) ? tableInfoMap.get(tableName) : null;
    }


    private static Class<?> getEntityClass(Class<?> mapperClass) {
        if (mapperClass == null || mapperClass == Object.class) {
            return null;
        }
        Type[] genericInterfaces = mapperClass.getGenericInterfaces();
        if (genericInterfaces.length == 1) {
            Type type = genericInterfaces[0];
            if (type instanceof ParameterizedType) {
                Type actualTypeArgument = ((ParameterizedType) type).getActualTypeArguments()[0];
                return actualTypeArgument instanceof Class ? (Class<?>) actualTypeArgument : null;
            } else if (type instanceof Class) {
                return getEntityClass((Class<?>) type);
            }
        }
        return getEntityClass(mapperClass.getSuperclass());
    }


    private static TableInfo createTableInfo(Class<?> entityClass) {

        TableInfo tableInfo = new TableInfo();
        tableInfo.setEntityClass(entityClass);
        Reflector reflector = Reflectors.of(entityClass);
        tableInfo.setReflector(reflector);

        //初始化表名
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null) {
            tableInfo.setSchema(table.schema());
            tableInfo.setTableName(table.value());
            tableInfo.setCamelToUnderline(table.camelToUnderline());

            if (table.onInsert().length > 0) {
                List<InsertListener> insertListeners = Arrays.stream(table.onInsert())
                    .filter(listener -> listener != NoneListener.class)
                    .map(ClassUtil::newInstance)
                    .collect(Collectors.toList());
                tableInfo.setOnInsertListeners(insertListeners);
            }

            if (table.onUpdate().length > 0) {
                List<UpdateListener> updateListeners = Arrays.stream(table.onUpdate())
                    .filter(listener -> listener != NoneListener.class)
                    .map(ClassUtil::newInstance)
                    .collect(Collectors.toList());
                tableInfo.setOnUpdateListeners(updateListeners);
            }

            if (table.onSet().length > 0) {
                List<SetListener> setListeners = Arrays.stream(table.onSet())
                    .filter(listener -> listener != NoneListener.class)
                    .map(ClassUtil::newInstance)
                    .collect(Collectors.toList());
                tableInfo.setOnSetListeners(setListeners);
            }

            if (StringUtil.isNotBlank(table.dataSource())) {
                tableInfo.setDataSource(table.dataSource());
            }
        } else {
            //默认为类名转驼峰下划线
            String tableName = StringUtil.camelToUnderline(entityClass.getSimpleName());
            tableInfo.setTableName(tableName);
        }

        //初始化字段相关
        List<ColumnInfo> columnInfoList = new ArrayList<>();
        List<IdInfo> idInfos = new ArrayList<>();


        String logicDeleteColumn = null;
        String versionColumn = null;
        String tenantIdColumn = null;

        //数据插入时，默认插入数据字段
        Map<String, String> onInsertColumns = new HashMap<>();

        //数据更新时，默认更新内容的字段
        Map<String, String> onUpdateColumns = new HashMap<>();

        //大字段列
        Set<String> largeColumns = new LinkedHashSet<>();

        // 默认查询列
        Set<String> defaultQueryColumns = new LinkedHashSet<>();

        List<Field> entityFields = getColumnFields(entityClass);

        FlexGlobalConfig config = FlexGlobalConfig.getDefaultConfig();

        for (Field field : entityFields) {

            Class<?> fieldType = reflector.getGetterType(field.getName());

            //移除默认的忽略字段
            boolean isIgnoreField = false;
            for (Class<?> ignoreColumnType : ignoreColumnTypes) {
                if (ignoreColumnType.isAssignableFrom(fieldType)) {
                    isIgnoreField = true;
                    break;
                }
            }

            if (isIgnoreField) {
                continue;
            }

            Column columnAnnotation = field.getAnnotation(Column.class);

            //满足以下 3 种情况，不支持该类型
            if ((columnAnnotation == null || columnAnnotation.typeHandler() == UnknownTypeHandler.class) // 未配置 typeHandler
                && !fieldType.isEnum()   // 类型不是枚举
                && !defaultSupportColumnTypes.contains(fieldType) //默认的自动类型不包含该类型
            ) {
                // 忽略 集合 实体类 解析
                if (columnAnnotation != null && columnAnnotation.ignore()) {
                    continue;
                }
                // 集合嵌套
                if (Collection.class.isAssignableFrom(fieldType)) {
                    Type genericType = TypeParameterResolver.resolveFieldType(field, entityClass);
                    if (genericType instanceof ParameterizedType) {
                        Type actualTypeArgument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                        if (actualTypeArgument instanceof Class) {
                            tableInfo.addCollectionType(field, (Class<?>) actualTypeArgument);
                        }
                    }
                }
                // 实体类嵌套
                else if (!Map.class.isAssignableFrom(fieldType)
                    && !fieldType.isArray()) {
                    tableInfo.addAssociationType(field.getName(), fieldType);
                }
                // 不支持的类型直接跳过
                continue;
            }

            //列名
            String columnName = getColumnName(tableInfo.isCamelToUnderline(), field, columnAnnotation);

            //逻辑删除字段
            if ((columnAnnotation != null && columnAnnotation.isLogicDelete())
                || columnName.equals(config.getLogicDeleteColumn())) {
                if (logicDeleteColumn == null) {
                    logicDeleteColumn = columnName;
                } else {
                    throw FlexExceptions.wrap("The logic delete column of entity[%s] must be less then 2.", entityClass.getName());
                }
            }

            //乐观锁版本字段
            if ((columnAnnotation != null && columnAnnotation.version())
                || columnName.equals(config.getVersionColumn())) {
                if (versionColumn == null) {
                    versionColumn = columnName;
                } else {
                    throw FlexExceptions.wrap("The version column of entity[%s] must be less then 2.", entityClass.getName());
                }
            }

            //租户ID 字段
            if ((columnAnnotation != null && columnAnnotation.tenantId())
                || columnName.equals(config.getTenantColumn())) {
                if (tenantIdColumn == null) {
                    tenantIdColumn = columnName;
                } else {
                    throw FlexExceptions.wrap("The tenantId column of entity[%s] must be less then 2.", entityClass.getName());
                }
            }


            if (columnAnnotation != null && StringUtil.isNotBlank(columnAnnotation.onInsertValue())) {
                onInsertColumns.put(columnName, columnAnnotation.onInsertValue().trim());
            }


            if (columnAnnotation != null && StringUtil.isNotBlank(columnAnnotation.onUpdateValue())) {
                onUpdateColumns.put(columnName, columnAnnotation.onUpdateValue().trim());
            }


            if (columnAnnotation != null && columnAnnotation.isLarge()) {
                largeColumns.add(columnName);
            }

            //主键配置
            Id id = field.getAnnotation(Id.class);
            ColumnInfo columnInfo;
            if (id != null) {
                columnInfo = new IdInfo(id);
                idInfos.add((IdInfo) columnInfo);
            } else {
                columnInfo = new ColumnInfo();
                columnInfoList.add(columnInfo);
            }

            ColumnAlias columnAlias = null;

            // 属性上没有别名，查找 getter 方法上有没有别名
            Method getterMethod = ClassUtil.getFirstMethod(entityClass, m -> ClassUtil.isGetterMethod(m, field.getName()));
            if (getterMethod != null) {
                columnAlias = getterMethod.getAnnotation(ColumnAlias.class);
            }

            if (columnAlias == null) {
                columnAlias = field.getAnnotation(ColumnAlias.class);
            }

            if (columnAlias != null) {
                columnInfo.setAlias(columnAlias.value());
            }

            columnInfo.setColumn(columnName);
            columnInfo.setProperty(field.getName());
            columnInfo.setPropertyType(fieldType);
            columnInfo.setIgnore(columnAnnotation != null && columnAnnotation.ignore());


            // 默认查询列 没有忽略且不是大字段
            if (columnAnnotation == null || (!columnAnnotation.isLarge() && !columnAnnotation.ignore())) {
                defaultQueryColumns.add(columnName);
            }


            //typeHandler 配置
            if (columnAnnotation != null && columnAnnotation.typeHandler() != UnknownTypeHandler.class) {
                TypeHandler<?> typeHandler;

                //集合类型，支持泛型
                //fixed https://gitee.com/mybatis-flex/mybatis-flex/issues/I7S2YE
                if (Collection.class.isAssignableFrom(fieldType)) {
                    typeHandler = createCollectionTypeHandler(entityClass, field, columnAnnotation.typeHandler(), fieldType);
                }

                //非集合类型
                else {
                    Class<?> typeHandlerClass = columnAnnotation.typeHandler();
                    Configuration configuration = FlexGlobalConfig.getDefaultConfig().getConfiguration();
                    TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
                    typeHandler = typeHandlerRegistry.getInstance(columnInfo.getPropertyType(), typeHandlerClass);
                }

                columnInfo.setTypeHandler(typeHandler);
            }

            // 数据脱敏配置
            ColumnMask columnMask = field.getAnnotation(ColumnMask.class);
            if (columnMask != null && StringUtil.isNotBlank(columnMask.value())) {
                if (String.class != fieldType) {
                    throw new IllegalStateException("@ColumnMask() only support for string type field. error: " + entityClass.getName() + "." + field.getName());
                }
                columnInfo.setMaskType(columnMask.value().trim());
            }

            // jdbcType 配置
            if (columnAnnotation != null && columnAnnotation.jdbcType() != JdbcType.UNDEFINED) {
                columnInfo.setJdbcType(columnAnnotation.jdbcType());
            }

        }


        tableInfo.setLogicDeleteColumn(logicDeleteColumn);
        tableInfo.setVersionColumn(versionColumn);
        tableInfo.setTenantIdColumn(tenantIdColumn);

        if (!onInsertColumns.isEmpty()) {
            tableInfo.setOnInsertColumns(onInsertColumns);
        }

        if (!onUpdateColumns.isEmpty()) {
            tableInfo.setOnUpdateColumns(onUpdateColumns);
        }

        if (!largeColumns.isEmpty()) {
            tableInfo.setLargeColumns(largeColumns.toArray(new String[0]));
        }

        if (!defaultQueryColumns.isEmpty()) {
            tableInfo.setDefaultQueryColumns(defaultQueryColumns.toArray(new String[0]));
        }

        // 此处需要保证顺序先设置 PrimaryKey，在设置其他 Column，
        // 否则会影响 SQL 的字段构建顺序
        tableInfo.setPrimaryKeyList(idInfos);
        tableInfo.setColumnInfoList(columnInfoList);


        return tableInfo;
    }

    /**
     * 创建 typeHandler
     * 参考 {@link TypeHandlerRegistry#getInstance(Class, Class)}
     *
     * @param entityClass
     * @param field
     * @param typeHandlerClass
     * @param fieldType
     */
    private static TypeHandler<?> createCollectionTypeHandler(Class<?> entityClass, Field field, Class<?> typeHandlerClass, Class<?> fieldType) {
        Class<?> genericClass = null;
        Type genericType = TypeParameterResolver.resolveFieldType(field, entityClass);
        if (genericType instanceof ParameterizedType) {
            Type actualTypeArgument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            if (actualTypeArgument instanceof Class) {
                genericClass = (Class<?>) actualTypeArgument;
            }
        }

        try {
            Constructor<?> constructor = typeHandlerClass.getConstructor(Class.class, Class.class);
            return (TypeHandler<?>) constructor.newInstance(fieldType, genericClass);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
        }
        try {
            Constructor<?> constructor = typeHandlerClass.getConstructor(Class.class);
            return (TypeHandler<?>) constructor.newInstance(fieldType);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
        }
        try {
            Constructor<?> c = typeHandlerClass.getConstructor();
            return (TypeHandler<?>) c.newInstance();
        } catch (Exception e) {
            throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
        }
    }


    static String getColumnName(boolean isCamelToUnderline, Field field, Column column) {
        if (column != null && StringUtil.isNotBlank(column.value())) {
            return column.value();
        }
        if (isCamelToUnderline) {
            return StringUtil.camelToUnderline(field.getName());
        }
        return field.getName();
    }


    public static List<Field> getColumnFields(Class<?> entityClass) {
        List<Field> fields = new ArrayList<>();
        doGetFields(entityClass, fields);
        return fields;
    }


    private static void doGetFields(Class<?> entityClass, List<Field> fields) {
        if (entityClass == null || entityClass == Object.class) {
            return;
        }

        Field[] declaredFields = entityClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            if (Modifier.isStatic(declaredField.getModifiers())
                || existName(fields, declaredField)) {
                continue;
            }
            fields.add(declaredField);
        }

        doGetFields(entityClass.getSuperclass(), fields);
    }


    private static boolean existName(List<Field> fields, Field field) {
        for (Field f : fields) {
            if (f.getName().equalsIgnoreCase(field.getName())) {
                return true;
            }
        }
        return false;
    }

}
