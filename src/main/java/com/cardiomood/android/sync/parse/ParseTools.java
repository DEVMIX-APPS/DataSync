package com.cardiomood.android.sync.parse;

import android.text.TextUtils;

import com.cardiomood.android.sync.annotations.ParseField;
import com.cardiomood.android.sync.ormlite.SyncEntity;
import com.cardiomood.android.sync.tools.ReflectionUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import bolts.Task;

/**
 * Created by danon on 14.08.2014.
 */
public final class ParseTools {

    private ParseTools() {
        // don't instantiate this!
    }

    public static final int DEFAULT_PARSE_QUERY_LIMIT = 100;

    public static String getUserFullName(ParseUser pu) {
        String fullName = pu.has("lastName") ? pu.getString("lastName") : "";
        if (!TextUtils.isEmpty(fullName))
            fullName += " ";
        if (pu.has("firstName"))
            fullName += pu.getString("firstName");
        return fullName;
    }

    public static <T extends ParseObject> Task<List<T>> findAllParseObjectsAsync(Class<T> clazz,Class<SyncEntity> entityClas) {
        return findAllParseObjectsAsync(ParseQuery.getQuery(clazz),entityClas);
    }

    public static <T extends ParseObject> Task<List<T>> findAllParseObjectsAsync(final ParseQuery<T> query, final Class<SyncEntity> entityClass) {
        return Task.callInBackground(new Callable<List<T>>() {
            @Override
            public List<T> call() throws Exception {
                return findAllParseObjects(query,entityClass);
            }
        });
    }

    public static <T extends ParseObject> List<T> findAllParseObjects(Class<T> clazz,Class<SyncEntity> entityClass) throws ParseException {
        return findAllParseObjects(ParseQuery.getQuery(clazz),entityClass);
    }

    public static <T extends ParseObject> List<T> findAllParseObjects(final ParseQuery<T> query,final Class entityClass) throws ParseException {
        List <T> result = new ArrayList<T>();
        query.setLimit(DEFAULT_PARSE_QUERY_LIMIT);

            ReflectionUtils.doWithFields(entityClass, new ReflectionUtils.FieldCallback() {
                @Override
                public void doWith(Field field, ParseObject parseObject, SyncEntity object) throws IllegalArgumentException, IllegalAccessException {
                    // extract Parse field name
                    ParseField fieldAnnotation = field.getAnnotation(ParseField.class);
                    if (fieldAnnotation == null)
                        return ;

                    String parseFieldName = field.getName();
                    if (fieldAnnotation.name() != null && !fieldAnnotation.name().isEmpty()) {
                        parseFieldName = fieldAnnotation.name();
                    }

                    if(fieldAnnotation.foreign()){
                        query.include(parseFieldName);
                    }

                    /*if(fieldAnnotation.file()){
                        query.include(parseFieldName);
                    }*/
                }

            }, null,null);


        List<T> chunk = null;
        do {
            chunk = query.find();
            result.addAll(chunk);
            query.setSkip(query.getSkip() + query.getLimit());
        } while (chunk.size() == query.getLimit());

        /*
        for(ParseObject o :result){
            ParseQuery<ParseUser> queryInner = ParseQuery.getQuery("_User");
            queryInner.whereEqualTo("objectId", o.getObjectId());
            queryInner.find();
        }*/

        return result;
    }
}
