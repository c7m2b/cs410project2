package com.program.analysis.app;

import java.io.File;
import java.io.IOException;

import com.program.analysis.app.representation.ProjectCollector;
import com.program.analysis.app.representation.ProjectParseException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ProjectCollectorTest {
    private ProjectCollector collector = ProjectCollector.getInstance();
    private static ZipFileStorageManager manager = new ZipFileStorageManager();

    @BeforeAll
    public static void setup() throws IOException {
        manager.setPaths("src/test/resources", "src/test/resources/unzip", false, true);
    }

    @Test
    public void testOneFileCourse() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/Course.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testDungeonElementNothingInside() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/DungeonElementNothingInside.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testJavaNameNotMatchClassName() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/javaNameNotMatchClassName.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testGoalWithNothingInside() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testGoalWithNothingInside.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testJavaWithParent() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testJavaWithParent.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testNoJava() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testNoJava.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(0, collector.getClasses().size());
    }

    @Test
    public void testOnly1JavaParentLost() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testOnly1JavaParentLost.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testOnlyOneAbstractJavaNoChildren() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testOnlyOneAbstractJavaNoChildren.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testWithJavaAndC() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testWithJavaAndC.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testWithTxtAndJava() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testWithTxtAndJava.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testWithWrongTypeJavaAndC() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testWithWrongTypeJavaAndC.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testBasicEnum() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/BasicEnum.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testEnumWithMethods() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/EnumWithMethods.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testExtendEnum() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/ExtendEnum.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testPrint() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/Print.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testTinyVarsEvaluator() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/tinyVarsEvaluator.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testTinyVarsVisitor() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/tinyVarsVisitor.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testClassWithPrivate() throws IOException, ProjectParseException {
        // Not Supported
        File[] files = manager.unzipFile("/ClassWithPrivate.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testClassWithPrivateInterface() throws IOException, ProjectParseException {
        // Not Supported
        File[] files = manager.unzipFile("/ClassWithPrivateInterface.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @Test
    public void testWhitespaceJavaNameNotMatchClassName() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/whitespaceJavaNameNotMatchClassName.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(2, collector.getClasses().size());
    }

    @Test
    public void testDependency() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/testDependency.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(4, collector.getClasses().size());
    }

    @Test
    public void testSampleProject() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/SampleProject.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(7, collector.getClasses().size());
    }

    @Test
    public void testSampleProject2() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/SampleForEach2.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(7, collector.getClasses().size());
    }

    @Test
    public void testCheckerForXNames() throws IOException, ProjectParseException {
        File[] files = manager.unzipFile("/CheckerForXNames.zip", true);
        collector.parseFiles(files);
        Assertions.assertEquals(1, collector.getClasses().size());
    }

    @AfterAll
    public static void cleanup() throws IOException {
        manager.deleteDestinationFolder();
    }
}
