package DSLModel;

import type.ConstructorToUse;
import type.MyDouble;
import type.MyString;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.FileSystemException;
import java.nio.file.NotDirectoryException;
import java.util.*;

public class CodeGenerator
{
    private File outputDir;
    private Entity model;
    private Scanner scanner = new Scanner(System.in);
    private ArrayList<Class> classes = new ArrayList<>();
    private AnalogInput analog;
    private HashMap<Class, String>sqlMap = new HashMap<>();

    public CodeGenerator(File outputDir)
    {
        this.outputDir = outputDir;
        checkDirectory();
    }

    public CodeGenerator(Entity model, File outputDir)
    {
        this.model = model;
        this.outputDir = outputDir;
        checkDirectory();
    }

    public void generateContents()
    {
        //Create Files
        try
        {
            createMainClass(createFiles(model.properties.get("EntityCode")+".java"));//generate $EntityCode.java
            createDaoClass(createFiles(model.properties.get("EntityCode")+"Dao.java"));
        }catch (CodeGenerateException e)
        {
            e.printStackTrace();
        }
        createFiles(model.properties.get("EntityCode")+"Dao.java");//generate $EntityCode.java
    }

    public void setSource(Entity model)
    {
        this.model = model;
    }

    public void setSqlMap(HashMap<Class, String> map)
    {
        this.sqlMap = map;
    }

    private void createMainClass(File file) throws CodeGenerateException
    {
        analog = new AnalogInput(file);
        analog.generateDescription(model.properties.get("EntityName"), model.properties.get("EntityDescription"));//generate description according to EntityNameSpace;
        analog.newLine();// Enter
        analog.newLine();// Enter
        analog.generatePackage(model.properties.get("EntityNameSpace"));//generate package according to EntityNameSpace;
        analog.newLine();// Enter
        analog.newLine();// Enter
        extractClasses();//create import statements
        analog.newLine();// Enter
        analog.createClass(model.properties.get("EntityCode"));//generate class name according to EntityCode;
        analog.newLine();// Enter
        analog.leftBrace();// {
        analog.newLine();// Enter
        analog.addIndent();// Tab
        generateFields();// generate fields
        analog.newLine();
        generateConstructor();// generate Constructor
        analog.revertIndent();
        analog.newLine();// Enter
        analog.rightBrace();
        analog.end();
    }

    private void createEnumClass(Field field) throws CodeGenerateException
    {
        AnalogInput analog_ = new AnalogInput(createFiles(model.properties.get("EntityCode")+field.properties.get("FieldCode")+".java"));
        analog_.generateFieldDescription(field.properties.get("FieldName"));
        analog_.newLine();
        analog_.newLine();
        analog_.generatePackage(model.properties.get("EntityNameSpace"));
        analog_.newLine();
        analog_.newLine();
        analog_.createEnum(model.properties.get("EntityCode")+field.properties.get("FieldCode"), (HashMap<Integer, String>) field.type);
        analog_.end();
    }

    private void createDaoClass(File file) throws CodeGenerateException
    {
        sqlMap.put(Integer.class, "INT");
        sqlMap.put(HashMap.class, "INT");
        sqlMap.put(Short.class, "SMALL INT");
        sqlMap.put(Long.class, "BIG INT");
        sqlMap.put(MyDouble.class, "DOUBLE");
        sqlMap.put(Boolean.class, "BOOL");
        sqlMap.put(MyString.class, "VARCHAR");
        sqlMap.put(Date.class, "DATETIME");
        AnalogInput analog_ = new AnalogInput(file);
        StringBuilder sql = new StringBuilder();

        /*process head part*/
        analog_.generateDescription(model.properties.get("EntityName"), model.properties.get("EntityDescription"));
        analog_.newLine();
        analog_.newLine();
        analog_.generatePackage(model.properties.get("EntityNameSpace"));
        analog_.newLine();
        analog_.newLine();
        analog_.importClass(java.sql.Connection.class);
        analog_.newLine();
        analog_.importClass(java.sql.DriverManager.class);
        analog_.newLine();
        analog_.importClass(java.sql.ResultSet.class);
        analog_.newLine();
        analog_.importClass(java.sql.Statement.class);
        analog_.newLine();
        analog_.newLine();
        analog_.createClass(model.properties.get("EntityCode")+"Dao");
        analog_.newLine();
        analog_.leftBrace();
        analog_.newLine();
        analog_.addIndent();

        /*process sql statement*/
        sql.append("CREATE TABLE ").append(model.properties.get("EntityTableCode")).append("(");
        for(Field field:model.fields)
        {
            sql.append(field.properties.get("FieldCode")).append(" ").append(sqlMap.get(field.type.getClass()));
            if(field.type instanceof MyString)
            {
                sql.append("(").append(((MyString) field.type).getMaxLength()).append(") ");
                if(!((MyString) field.type).isEmpty())sql.append("NOT NULL ");
            }
            if(field.type instanceof MyDouble)
            {
                sql.append("(").append(((MyDouble) field.type).getLength()).append(", ").append(((MyDouble) field.type).getPrecision()).append(")");
            }
            sql.append(", ");
        }
        sql.delete(sql.length()-2,sql.length());
        sql.append(")ENGINE=InnoDB DEFAULT CHARSET=utf8;");
        analog_.createStaticSqlConstructor(sql.toString());
        analog_.newLine();
        analog_.createDaoMain();
        analog_.revertIndent();
        analog_.newLine();
        analog_.rightBrace();
        analog_.end();
    }

    private void extractClasses() throws CodeGenerateException
    {
        HashSet<Class> bufferSets = new HashSet<>();// use Set to avoid repeat import statements
        for(Field field : model.fields)
        {
            if(!(field.type instanceof HashMap))
                bufferSets.add(field.type.getClass());
        }
        for(Class class_ : bufferSets)
        {
            analog.importClass(class_);
            analog.newLine();
        }
    }

    private void generateConstructor() throws CodeGenerateException
    {
        StringBuilder paras = new StringBuilder();// use StringBuilder to enhance efficiency
        for(Field field : model.fields)
        {
            if(field.type instanceof MyString)
                if(!((MyString)field.type).isEmpty())
                {
                    paras.append("String ").append(field.properties.get("FieldCode")).append(", ");
                }
        }
        paras.delete(paras.length()-2, paras.length());
        analog.generateConstructor(model.properties.get("EntityCode"), paras.toString());
    }

    private void generateFields() throws CodeGenerateException
    {
        for(Field field : model.fields)
        {
            boolean found = false;//whether find annotation or not
            //if type equals HashMap, then create Enum class
            if(field.type instanceof HashMap)
            {
                createEnumClass(field);
                analog.createFieldWithoutConstructor(model.properties.get("EntityCode")+field.properties.get("FieldCode"), field.properties.get("FieldCode"));
            }
            /*customized user Class, use Java Reflect mechanism*/
            else for(Constructor constructor:field.type.getClass().getConstructors())
            {
                if(constructor.getAnnotation(ConstructorToUse.class)!=null)
                {
                    found = true;
                    ConstructorToUse annotation = (ConstructorToUse) constructor.getAnnotation(ConstructorToUse.class);
                    if(((ConstructorToUse)constructor.getAnnotation(ConstructorToUse.class)).doConstruct())//if field needs to construct itself.
                    {
                        StringBuilder paras = new StringBuilder();
                        for(String para:annotation.initializedVariable())
                        {
                            try
                            {
                                java.lang.reflect.Field field_ = field.type.getClass().getDeclaredField(para);
                                field_.setAccessible(true);
                                paras.append(field_.get(field.type).toString()).append(", ");
                            } catch (IllegalAccessException | NoSuchFieldException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        paras.delete(paras.length()-2,paras.length());
                        analog.createFieldWithConstructor(field.type.getClass().getSimpleName(), field.properties.get("FieldCode"), paras.toString());
                    }
                    else//no need to construct
                    {
                        analog.createFieldWithoutConstructor(field.type.getClass().getSimpleName(), field.properties.get("FieldCode"));
                    }
                    break;
                }
            }
            //if not found, generate no constructor field.
            if(!found && !(field.type instanceof HashMap))analog.createFieldWithoutConstructor(field.type.getClass().getSimpleName(), field.properties.get("FieldCode"));
            analog.generateFieldDescription(field.properties.get("FieldName"));
            analog.newLine();
        }
    }

    private File createFiles(String file)
    {
        File fileCreating = new File(outputDir.getAbsolutePath()+System.getProperty("file.separator")+file);//handle windows and linux path compatibility

            try
            {
                if(!fileCreating.exists()&&fileCreating.createNewFile())
                    System.out.println("Created new Files.");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        return fileCreating;
    }

    private void checkDirectory()
    {
        try
        {
            if(!outputDir.exists())//文件夹不存在
            {
                if(!outputDir.mkdir())throw new FileSystemException(outputDir.getName());//创建文件夹失败
            }
            else if(!outputDir.isDirectory())//文件或文件夹存在 但传入的不是文件夹
            {
                throw new NotDirectoryException(outputDir.getName());
            }
            System.out.println("Directory checked!");
        }
        catch(IOException e)
        {
            e.printStackTrace();
            System.out.print("Please Try Input Output Directory Again: ");
            this.outputDir = new File(scanner.next());
            checkDirectory();
        }
    }
}
