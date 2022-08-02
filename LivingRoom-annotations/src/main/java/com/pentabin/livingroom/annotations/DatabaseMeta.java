package com.pentabin.livingroom.annotations;

import androidx.room.AutoMigration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DatabaseMeta {
    int version() default 1;
    boolean exportSchema() default true;
    Class<?> databaseCallbacks() default Object.class;
    AutoMigration[] autoMigrations() default {};
}
