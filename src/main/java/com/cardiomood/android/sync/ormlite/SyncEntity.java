package com.cardiomood.android.sync.ormlite;

import com.cardiomood.android.sync.annotations.ParseClass;
import com.cardiomood.android.sync.annotations.ParseField;
import com.cardiomood.android.sync.parse.ParseValueConverter;
import com.cardiomood.android.sync.tools.ReflectionUtils;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.parse.ParseFile;
import com.parse.ParseObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * Created by antondanhsin on 08/10/14.
 */
public abstract class SyncEntity {

    @DatabaseField(columnName = "sync_id", unique = true)
    private String syncId;

    @DatabaseField(columnName = "sync_timestamp", dataType = DataType.DATE_LONG)
    private Date syncDate;

    @DatabaseField(columnName = "creation_timestamp", dataType = DataType.DATE_LONG)
    private Date creationDate;

    @DatabaseField(columnName = "deleted", dataType = DataType.BOOLEAN)
    @ParseField(name = "deleted")
    private boolean deleted;

    public String getSyncId() {
        return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
    }

    public Date getSyncDate() {
        return syncDate;
    }

    public void setSyncDate(Date syncDate) {
        this.syncDate = syncDate;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public static <T extends SyncEntity> SyncEntity fromParseObject(ParseObject parseObject, T entity, final boolean innerClass, final OrmLiteSqliteOpenHelper dbHelper) {
        try {
            // extract value converter
            Class<? extends SyncEntity> entityClass = entity.getClass();
            ParseClass classAnnotation = entityClass.getAnnotation(ParseClass.class);

            if (classAnnotation == null) {
                throw new IllegalArgumentException("Class " + entityClass.getName() + " must declare annotation " + ParseClass.class.getName());
            }
            // TODO: converters must be cached!
            final ParseValueConverter converter = classAnnotation.valueConverterClass().newInstance();

            ReflectionUtils.doWithFields(
                    entityClass,
                    new ReflectionUtils.FieldCallback() {
                        @Override
                        public void doWith(Field field, ParseObject parseObject, SyncEntity entity) throws IllegalArgumentException, IllegalAccessException {

                            // extract Parse field name
                            ParseField fieldAnnotation = field.getAnnotation(ParseField.class);
                            if (fieldAnnotation == null)
                                return ;
                            String parseFieldName = field.getName();
                            if (fieldAnnotation.name() != null && !fieldAnnotation.name().isEmpty()) {
                                parseFieldName = fieldAnnotation.name();
                            }

                            try {
                                boolean accessible = field.isAccessible();
                                field.setAccessible(true);
                                Object remoteValue = parseObject.get(parseFieldName);
                                Class localValueType = field.getType();

                                if(remoteValue != null) {
                                    if (fieldAnnotation.foreign() && innerClass) {

                                        String parseClassName = localValueType.getSimpleName();

                                        ParseObject parseRemoteObject = (ParseObject) remoteValue;

                                        Object objectParse = parseRemoteObject.get(parseFieldName);

                                        List listInnerObject = null;
                                        try {

                                            String syncId = parseRemoteObject.getObjectId();
                                            listInnerObject = dbHelper.getDao(localValueType).queryForEq("sync_id", syncId);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        if (listInnerObject != null && !listInnerObject.isEmpty()) {

                                            field.set(entity, listInnerObject.get(0));
                                        } else {

                                            try {
                                                Object o = converter.convertValue(fromParseObject(parseRemoteObject, (SyncEntity) localValueType.newInstance(), false, dbHelper), localValueType);
                                                field.set(entity, listInnerObject);
                                            } catch (InstantiationException e) {
                                                e.printStackTrace();
                                            }


                                        }

                                    } else {

                                        if (fieldAnnotation.file()) {

                                            ParseFile file = (ParseFile) remoteValue;
                                            field.set(entity, file.getData());

                                        } else {
                                            field.set(entity, converter.convertValue(remoteValue, localValueType));
                                        }

                                    }

                                }



                                // restore accessible flag
                                field.setAccessible(accessible);
                            } catch (Exception ex) {
                                throw new RuntimeException("Failed to process field "
                                        + field.getName() + " with annotation " + fieldAnnotation);
                            }

                        }

                    },parseObject,entity
            );

            entity.setSyncId(parseObject.getObjectId());
            entity.setSyncDate(parseObject.getUpdatedAt());
            entity.setCreationDate(parseObject.getCreatedAt());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return entity;
    }

    public static <T extends SyncEntity> T fromParseObject(ParseObject parseObject, Class<T> entityClass, OrmLiteSqliteOpenHelper dbHelper) {
        try {
            Object entity = entityClass.newInstance();
            entity = fromParseObject(parseObject, (SyncEntity) entity,true,dbHelper);
            return (T) entity;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T extends SyncEntity, P extends ParseObject> Object toParseObject(SyncEntity entity, P parseObject, final boolean innerClass) {
        try {
            Class entityClass = entity.getClass();

            // update objectId
            if (entity.getSyncId() != null) {
                Field objectIdField = ParseObject.class.getDeclaredField("objectId");
                objectIdField.setAccessible(true);
                objectIdField.set(parseObject, entity.getSyncId());
                objectIdField.setAccessible(false);
            }

            // update createdAt
            if (entity.getCreationDate() != null) {
                Field createdAtField = ParseObject.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(parseObject, entity.getCreationDate());
                createdAtField.setAccessible(false);
            }

            // update updatedAt
            if (entity.getSyncDate() != null) {
                Field updatedAtField = ParseObject.class.getDeclaredField("updatedAt");
                updatedAtField.setAccessible(true);
                updatedAtField.set(parseObject, entity.getSyncDate());
                updatedAtField.setAccessible(false);
            }

            ReflectionUtils.doWithFields(
                    entityClass,
                    new ReflectionUtils.FieldCallback() {
                        @Override
                        public void doWith(Field field, ParseObject parseObject, SyncEntity entity) throws IllegalArgumentException, IllegalAccessException {
                            ParseField a = field.getAnnotation(ParseField.class);
                            if (a == null)
                                return;
                            String parseFieldName = field.getName();
                            if (a.name() != null && !a.name().isEmpty()) {
                                parseFieldName = a.name();
                            }

                            boolean accessible = field.isAccessible();
                            field.setAccessible(true);
                            Object value = field.get(entity);
                            if (value != null) {
                                if (!a.foreign()) {
                                    parseObject.put(parseFieldName, value);
                                } else {

                                    if (innerClass) {
                                        String parseClassName = value.getClass().getSimpleName();

                                        ParseObject innerClass = new ParseObject(parseClassName);

                                        if (a.name() != null && !a.name().isEmpty()) {

                                            try {
                                                parseObject.put(parseFieldName, toParseObject((SyncEntity) ReflectionUtils.getMethodValue("get"+parseClassName,value.getClass(),entity),parseObject,false));
                                            } catch (NoSuchMethodException e) {
                                                e.printStackTrace();
                                            } catch (InvocationTargetException e) {
                                                e.printStackTrace();
                                            }

                                        }

                                    }
                                }
                            } else parseObject.remove(parseFieldName);
                            field.setAccessible(accessible);

                        }

                    },
            parseObject,entity);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return parseObject;
    }

    public static <T extends SyncEntity> ParseObject toParseObject(T entity) {
        try {
            Class entityClass = entity.getClass();
            ParseClass a = (ParseClass) entityClass.getAnnotation(ParseClass.class);
            String parseClassName = entityClass.getSimpleName();

            if (a.name() != null && !a.name().isEmpty()) {
                parseClassName = a.name();
            }

            ParseObject parseObject = ParseObject.create(parseClassName);
            toParseObject(entity, parseObject,true);

            return parseObject;

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
