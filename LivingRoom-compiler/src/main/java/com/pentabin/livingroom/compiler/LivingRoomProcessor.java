package com.pentabin.livingroom.compiler;

import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.TypeConverters;

import com.pentabin.livingroom.annotations.Archivable;
import com.pentabin.livingroom.annotations.Crudable;
import com.pentabin.livingroom.annotations.DatabaseMeta;
import com.pentabin.livingroom.annotations.DatabaseOnCreate;
import com.pentabin.livingroom.annotations.DatabaseOnDestructiveMigration;
import com.pentabin.livingroom.annotations.DatabaseOnOpen;
import com.pentabin.livingroom.annotations.Deletable;
import com.pentabin.livingroom.annotations.Insertable;
import com.pentabin.livingroom.annotations.ManualMigrationMethod;
import com.pentabin.livingroom.annotations.SelectableAll;
import com.pentabin.livingroom.annotations.SelectableById;
import com.pentabin.livingroom.annotations.SelectableWhere;
import com.pentabin.livingroom.annotations.SelectableWheres;
import com.pentabin.livingroom.annotations.Updatable;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;

import static com.pentabin.livingroom.compiler.LivingroomMethod.GET_ALL;
import static com.pentabin.livingroom.compiler.LivingroomMethod.GET_BY_ID;
import static com.pentabin.livingroom.compiler.LivingroomMethod.INSERT;
import static com.pentabin.livingroom.compiler.LivingroomMethod.selectWhereMethod;

/**
 *
 */
@SupportedAnnotationTypes(
        {
                "com.pentabin.livingroom.annotations.Crudable",
                "com.pentabin.livingroom.annotations.Insertable",
                "com.pentabin.livingroom.annotations.Deletable",
                "com.pentabin.livingroom.annotations.Updatable",
                "com.pentabin.livingroom.annotations.Archivable",
                "com.pentabin.livingroom.annotations.SelectableAll",
                "com.pentabin.livingroom.annotations.SelectableById",
                "com.pentabin.livingroom.annotations.SelectableWhere",
                "com.pentabin.livingroom.annotations.SelectableWheres",
                "com.pentabin.livingroom.annotations.DatabaseExtras",
        })
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LivingRoomProcessor extends AbstractProcessor {

    private List<TypeName> entities;
    private HashMap<TypeElement, EntityClass> entitiesList;
    private static  String packageName; //TODO from the database class maybe?
    private static final String SUFFIX_DAO = "Dao"; // Todo remove
    static final String dbClassName = "LivingRoomDatabase";


    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        entities = new ArrayList<>();
        entitiesList = new HashMap<>();
    }

    public LivingRoomProcessor(){}

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment env) {
        parseCrudable(env);
        parseInsertable(env);
        parseDeletable(env);
        parseUpdatable(env);
        parseArchivable(env);
        parseSelectable(env);
        parseSelectables(env);
        parseSelectableAll(env);
        parseSelectableById(env);
        parseDatabaseExtras(env);
        parseDatabaseManualMigrations(env);

        try {
            generateClasses();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void  parseAnnotation(Collection<? extends Element> elements, String method) {
        for (Element e: elements ) {
            checkIfAnnotatedWithEntity(e);
            checkIfExtendsBasicEntity(e);
            if (entitiesList.containsKey(e)) {
                EntityClass entityClass = entitiesList.get(e);
                entitiesList.get(e).addMethod(LivingroomMethod.of(entityClass, method));
            }
            else {
                EntityClass entityClass = new EntityClass((TypeElement)e);
                entityClass.addMethod(LivingroomMethod.of(entityClass, method));
                entitiesList.put((TypeElement) e, entityClass);
            }
        }
    }

    private void generateClasses() throws IOException {
        for (Map.Entry<TypeElement, EntityClass> e: entitiesList.entrySet()) {
            generateCodeForEntity(e.getValue());
            entities.add(e.getValue().getTypeName());
        }

        try {
            generateDatabaseClass();
        } catch (FilerException e){

        }
    }

    private void parseCrudable(RoundEnvironment env) {
        Collection<? extends Element> elements =
                env.getElementsAnnotatedWith(Crudable.class);
        for (Element e: elements ) {
            if (entitiesList.containsKey(e)) {
                EntityClass entityClass = entitiesList.get(e);
                entitiesList.get(e).addMethods(LivingroomMethod.crud(entityClass));
            }
            else {
                EntityClass entityClass = new EntityClass((TypeElement)e);
                entityClass.addMethods(LivingroomMethod.crud(entityClass));
                entitiesList.put((TypeElement) e, entityClass);
            }
        }
    }

    private void parseInsertable(RoundEnvironment env) {
        Collection<? extends Element> insertableElements =
                env.getElementsAnnotatedWith(Insertable.class);
        parseAnnotation(insertableElements, INSERT);
    }

    private void parseDeletable(RoundEnvironment env) {
        Collection<? extends Element> deletableElements =
                env.getElementsAnnotatedWith(Deletable.class);
        parseAnnotation(deletableElements, LivingroomMethod.DELETE);
    }

    private void parseUpdatable(RoundEnvironment env) {
        Collection<? extends Element> updatableElements =
                env.getElementsAnnotatedWith(Updatable.class);
        parseAnnotation(updatableElements, LivingroomMethod.UPDATE);
    }


    private void parseArchivable(RoundEnvironment env) {
        Collection<? extends Element> archivableElements =
                env.getElementsAnnotatedWith(Archivable.class);
        parseAnnotation(archivableElements, LivingroomMethod.SOFT_DELETE);
    }

    private void parseSelectableAll(RoundEnvironment env) {
        Collection<? extends Element> archivableElements =
                env.getElementsAnnotatedWith(SelectableAll.class);
        parseAnnotation(archivableElements, GET_ALL);
    }

    private void parseSelectableById(RoundEnvironment env) {
        Collection<? extends Element> archivableElements =
                env.getElementsAnnotatedWith(SelectableById.class);
        parseAnnotation(archivableElements, GET_BY_ID);
    }

    private String versionValue = "1";
    private String exportSchemaValue = "true";
    private String autoMigrationRawCode = "{}";
    private String databaseCallbackQualifiedClassName;
    private String onOpenMethodName = "";
    private String onCreateMethodName = "";
    private String onDestructiveMigrationMethodName = "";

    private void parseDatabaseCallbackClass(String databaseCallbackSimpleClassName, RoundEnvironment env) {

        Collection<? extends Element> onOpenElement =
                env.getElementsAnnotatedWith(DatabaseOnOpen.class);

        for(Element element : onOpenElement) {
            //Ensuring this method is defined inside the Database Callback class specified in the Database Meta annotated class
            if(element.getEnclosingElement().getSimpleName().contentEquals(databaseCallbackSimpleClassName)) {
                onOpenMethodName = databaseCallbackQualifiedClassName+"."+element.getSimpleName().toString()+"(db);";
                break; //allowing only one method to exist
            }
        }

        Collection<? extends Element> onCreateElement =
                env.getElementsAnnotatedWith(DatabaseOnCreate.class);

        for(Element element : onCreateElement) {
            //Ensuring this method is defined inside the Database Callback class specified in the Database Meta annotated class
            if(element.getEnclosingElement().getSimpleName().contentEquals(databaseCallbackSimpleClassName)) {
                onCreateMethodName = databaseCallbackQualifiedClassName+"."+element.getSimpleName().toString()+"(db);";
                break; //allowing only one method to exist
            }
        }

        Collection<? extends Element> onDestructiveMigrationElement =
                env.getElementsAnnotatedWith(DatabaseOnDestructiveMigration.class);

        for(Element element : onDestructiveMigrationElement) {
            //Ensuring this method is defined inside the Database Callback class specified in the Database Meta annotated class
            if(element.getEnclosingElement().getSimpleName().contentEquals(databaseCallbackSimpleClassName)) {
                onDestructiveMigrationMethodName = databaseCallbackQualifiedClassName+"."+element.getSimpleName().toString()+"(db);";
                break; //allowing only one method to exist
            }
        }

    }

    private ArrayList<String> manualMigrations = new ArrayList<>();

    private void parseDatabaseManualMigrations(RoundEnvironment env) {
        Collection<? extends Element> manualMigrationMethodElements =
                env.getElementsAnnotatedWith(ManualMigrationMethod.class);
        for(Element manualMigrationMethodElement : manualMigrationMethodElements) {

            Element enclosingElement = manualMigrationMethodElement.getEnclosingElement();

            String migrationMethodToBecalled = enclosingElement.toString()+"."+manualMigrationMethodElement.getSimpleName();
            //processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, );


            for (AnnotationMirror annotationElement : enclosingElement.getAnnotationMirrors()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,annotationElement.getElementValues().toString());
                String from = null;
                String to = null;
                for(ExecutableElement key : annotationElement.getElementValues().keySet()) {
                    if(key.getSimpleName().contentEquals("from")) {
                        from = annotationElement.getElementValues().get(key).toString();
                        //processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, annotationElement.getElementValues().get(key).toString());
                    } else if(key.getSimpleName().contentEquals("to")) {
                        to = annotationElement.getElementValues().get(key).toString();
                        //processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, annotationElement.getElementValues().get(key).toString());
                    }
                }
                annotationElement.getElementValues().get("from");
                manualMigrations.add("new androidx.room.migration.Migration("+from+", "+to+") {\n" +
                        "               @Override\n" +
                        "               public void migrate(androidx.sqlite.db.SupportSQLiteDatabase database) {\n" +
                        "                   "+migrationMethodToBecalled+"(database);\n"+
                        "               }}");
            }
        }
    }

    private void parseDatabaseExtras(RoundEnvironment env) {

        Collection<? extends Element> databaseExtraElements =
                env.getElementsAnnotatedWith(DatabaseMeta.class);

        if(databaseExtraElements.size()>1) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "This version of Rooms boilerplate code generator supports only one 'Database Meta' corresponding to one 'Database' for the App!");
        } else if(databaseExtraElements.size()!=0) {

            //We have atleast one class defining the database extras
            for(Element extras : databaseExtraElements) {
                //Processing class annotated with DatabaseMeta
                for(AnnotationMirror annotationElement : extras.getAnnotationMirrors()) {
                    //Processing all annotation values
                    for(ExecutableElement  annotationValues : annotationElement.getElementValues().keySet()){
                        if(annotationValues.getSimpleName().contentEquals("version")) {
                            //Saving passed version number
                            versionValue = annotationElement.getElementValues().get(annotationValues).getValue().toString();
                        } else if(annotationValues.getSimpleName().contentEquals("exportSchema")) {
                            //saving passed exportSchema boolean
                            exportSchemaValue = annotationElement.getElementValues().get(annotationValues).getValue().toString();
                        } else if(annotationValues.getSimpleName().contentEquals("autoMigrations")) {
                            //saving autoMigrations details
                            String autoMigrationsValue = annotationElement.getElementValues().get(annotationValues).getValue().toString();
                            autoMigrationRawCode = "{\n" +
                                    "    " + autoMigrationsValue.toString() + ", " +
                                    "  }";
                            //processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, autoMigrationRawCode);
                        } else if(annotationValues.getSimpleName().contentEquals("databaseCallbacks")) {
                            //processing if database callbacks are defined
                            databaseCallbackQualifiedClassName = annotationElement.getElementValues().get(annotationValues).getValue().toString();
                            String databaseCallbackSimpleClassName = databaseCallbackQualifiedClassName.substring(databaseCallbackQualifiedClassName.lastIndexOf('.') + 1);
                            parseDatabaseCallbackClass(databaseCallbackSimpleClassName, env);
                        }
                    }
                }
            }
        }
    }

    private void parseSelectable(RoundEnvironment env) { // TODO refactor
        Collection<? extends Element> elements =
                env.getElementsAnnotatedWith(SelectableWhere.class);

        for (Element e: elements ) {
            SelectableWhere a = e.getAnnotation(SelectableWhere.class);
            if (entitiesList.containsKey(e)) {
                entitiesList.get(e).addMethod(
                        selectWhereMethod(entitiesList.get(e),
                                a.methodName(),
                                a.where(),
                                a.params(), true));
            } else {
                EntityClass entityClass = new EntityClass((TypeElement) e);
                entityClass.addMethod(
                        selectWhereMethod(entitiesList.get(e),
                                a.methodName(),
                                a.where(),
                                a.params(), true));
                entitiesList.put((TypeElement) e, entityClass);
            }
        }
    }

    private void parseSelectables(RoundEnvironment env) { // TODO refactor
        Collection<? extends Element> elements =
                env.getElementsAnnotatedWith(SelectableWheres.class);

        for (Element e: elements ) {
            for (SelectableWhere a: e.getAnnotation(SelectableWheres.class).value() ) {

                if (entitiesList.containsKey(e)) {
                    entitiesList.get(e).addMethod(
                            selectWhereMethod(entitiesList.get(e),
                                    a.methodName(),
                                    a.where(),
                                    a.params(), true));
                } else {
                    EntityClass entityClass = new EntityClass((TypeElement) e);
                    entityClass.addMethod(
                            selectWhereMethod(entitiesList.get(e),
                                    a.methodName(),
                                    a.where(),
                                    a.params(), true));
                    entitiesList.put((TypeElement) e, entityClass);
                }
            }
        }
    }

    private void generateCodeForEntity(EntityClass clazz) throws IOException {
        String path = clazz.getTypeElement().getQualifiedName().toString();
        if (packageName == null) { // TODO get out package from here (only for the database)
            int lastDot = path.lastIndexOf('.');
            if (lastDot > 0) {
                packageName = path.substring(0, lastDot);
            }
        }
        try {
            generateDaoClass(clazz);
            generateRepositoryClass(clazz);
            generateViewModelClass(clazz);
        } catch (FilerException e) {

        }

    }

    private void checkIfExtendsBasicEntity(Element annotatedElement){
        TypeElement superClassTypeElement =
                (TypeElement)((DeclaredType)((TypeElement)annotatedElement).getSuperclass()).asElement();
        if (!superClassTypeElement.getSimpleName().toString().equals("BasicEntity"))
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Your entity class must inherit from BasicEntity in order to use LivingRoom annotations", annotatedElement);

    }
    private void checkIfAnnotatedWithEntity(Element annotatedElement){
        if (annotatedElement.getAnnotation(Entity.class) == null)
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Please consider marking your class as a room @Entity", annotatedElement);

    }

    private void generateDaoClass(EntityClass clazz) throws IOException {
        JavaFile javaFile = JavaFile.builder(packageName, clazz.generateDaoClass()).build();
        Filer filer = processingEnv.getFiler();
        //javaFile.writeTo(System.out);
        javaFile.writeTo(filer);
    }

    private void generateViewModelClass(EntityClass clazz) throws IOException {
        JavaFile javaFile = JavaFile.builder(packageName, clazz.generateViewModelClass()).build();
        Filer filer = processingEnv.getFiler();
        //javaFile.writeTo(System.out);
        javaFile.writeTo(filer);
    }

    private void generateRepositoryClass(EntityClass clazz) throws IOException {
        JavaFile javaFile = JavaFile.builder(packageName, clazz.generateRepositoryClass()).build();
        Filer filer = processingEnv.getFiler();
        //javaFile.writeTo(System.out);
        javaFile.writeTo(filer);
    }

    private void generateDatabaseClass() throws IOException {
        final String instanceName = "INSTANCE";
        final String dataBaseName = "custom_database";
        StringBuilder listEntities = new StringBuilder("{");
        List<MethodSpec> listDaoMethods = new ArrayList<>();

        for (TypeName entity: entities) {
            int lastDot = entity.toString().lastIndexOf('.');
            String entityClassName = entity.toString().substring(lastDot + 1);
            listEntities.append(entityClassName).append(".class, ");
            listDaoMethods.add(
                    MethodSpec.methodBuilder(
                                    (entityClassName+ SUFFIX_DAO).toLowerCase())
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .returns(ClassName.get(packageName, entityClassName+ SUFFIX_DAO))
                            .build());
        }

        FieldSpec instance = FieldSpec.builder(ClassName.get(packageName, dbClassName), instanceName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.VOLATILE).build();
        listEntities.append("}");

        MethodSpec getDatabase = MethodSpec.methodBuilder("getDatabase")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.get("android.content",
                                "Context"),
                        "context",
                        Modifier.FINAL)
                .addCode("if ($N == null) {\n" +
                                "    synchronized ($T.class) {\n" +
                                "        if ($N == null) {\n" +
                                "            $N = androidx.room.Room.databaseBuilder(context.getApplicationContext(),\n" +
                                "                    $T.class, \""+dataBaseName+"\")\n" +
                                "                    .fallbackToDestructiveMigration()\n" +
                                "                    .enableMultiInstanceInvalidation()\n"+
                                "                    .addCallback(new RoomDatabase.Callback() {\n" +
                                "                       @Override\n" +
                                "                       public void onCreate(androidx.sqlite.db.SupportSQLiteDatabase db) {\n" +
                                "                           super.onCreate(db);\n" +
                                "                           "+onCreateMethodName+"\n" +
                                "                           //com.pentabin.livingroom.DatabaseCallbacksKt.onDatabaseCreate(db);\n" +
                                "                       }\n" +
                                "\n" +
                                "                       @Override\n" +
                                "                       public void onOpen(androidx.sqlite.db.SupportSQLiteDatabase db) {\n" +
                                "                           super.onOpen(db);\n" +
                                "                           "+onOpenMethodName+"\n" +
                                "                       }\n" +
                                "\n" +
                                "                       @Override\n" +
                                "                       public void onDestructiveMigration(androidx.sqlite.db.SupportSQLiteDatabase db) {\n" +
                                "                           super.onDestructiveMigration(db);\n" +
                                "                           "+onDestructiveMigrationMethodName+"\n" +
                                "                       }\n" +
                                "                       })\n"+
                                "                   .addMigrations("+String.join(",",manualMigrations)+")\n" +
                                "                   .build();\n" +
                                "        }\n" +
                                "    }\n" +
                                "}\n" +
                                "return $N;",
                        instanceName, ClassName.get(packageName, dbClassName) , instanceName, instanceName, ClassName.get(packageName, dbClassName), instanceName)
                .returns(ClassName.get(packageName, dbClassName))
                .build();

        TypeSpec dbClass = TypeSpec.classBuilder(dbClassName)
                .superclass(ClassName.get("androidx.room", "RoomDatabase"))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(AnnotationSpec.builder(Database.class)
                        .addMember("entities", listEntities.toString())
                        .addMember("version", versionValue)
                        .addMember("exportSchema", exportSchemaValue)
                        .addMember("autoMigrations", autoMigrationRawCode)
                        .build())
                .addAnnotation(AnnotationSpec.builder(TypeConverters.class)
                        .addMember("value", "$T.class", ClassName.get("com.pentabin.livingroom", "DateConverter") )
                        .build())
                .addField(instance)
                .addMethods(listDaoMethods)
                .addMethod(getDatabase)
                .build();

        Filer filer = processingEnv.getFiler();
        JavaFile javaFile = JavaFile.builder(packageName, dbClass)
                .build();
        javaFile.writeTo(filer);
    }

}

