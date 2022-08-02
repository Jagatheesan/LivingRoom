package com.pentabin.livingroom.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ManualMigration {
    /**
     * Version of the database schema to migrate from.
     *
     * @return Version number of the database to migrate from.
     */
    int from();

    /**
     * Version of the database schema to migrate to.
     *
     * @return Version number of the database to migrate to.
     */
    int to();
}
