package io.github.marmer.testutils.samplepojos;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.jci.compilers.CompilationResult;
import org.apache.commons.jci.compilers.JavaCompiler;
import org.apache.commons.jci.compilers.JavaCompilerFactory;
import org.apache.commons.jci.compilers.JavaCompilerSettings;
import org.apache.commons.jci.readers.FileResourceReader;
import org.apache.commons.jci.stores.FileResourceStore;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import io.github.marmer.testutils.BeanPropertyMatcher;
import lombok.Value;

public class HasPropertyMatcherGeneratorTest {
	private static final String JAVA_FILE_POSTFIX = ".java";
	private static final String SOURCE_ENCODING = "UTF-8";
	private static final String JAVA_VERSION = "1.7";
	private static final String MATCHER_POSTFIX = "Matcher";
	@Rule
	public final MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);
	@InjectMocks
	private HasPropertyMatcherGenerator classUnderTest;
	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();
	private Path srcOutputDir;
	private final JavaCompiler compiler = new JavaCompilerFactory().createCompiler("eclipse");
	private final JavaCompilerSettings compilerSettings = compiler.createDefaultSettings();
	private Path classOutputDir;
	private ClassLoader classLoader;

	@Before
	public void setUp() throws Exception {
		prepareSourceOutputDir();
		prepareOutputDir();
		prepareClassLoader();
		initCompilerSettings();
	}

	public void prepareOutputDir() throws Exception {
		this.classOutputDir = temp.newFolder("target").toPath();

	}

	private void prepareClassLoader() throws MalformedURLException {
		URL url = classOutputDir.toUri().toURL();
		classLoader = new URLClassLoader(new URL[] { url });
	}

	public void prepareSourceOutputDir() throws Exception {
		this.srcOutputDir = temp.newFolder("src").toPath();
	}

	public void initCompilerSettings() {
		compilerSettings.setSourceEncoding(SOURCE_ENCODING);
		compilerSettings.setSourceVersion(JAVA_VERSION);
		compilerSettings.setTargetVersion(JAVA_VERSION);
	}

	@Test
	public void testGenerateMatcherFor_SimplePojoClassGiven_ShouldCreateJavaFile() throws Exception {
		// Preparation
		classUnderTest.generateMatcherFor(SimplePojo.class, srcOutputDir);

		// Assertion
		assertThat(generatedSourceFileFor(SimplePojo.class), is(anExistingFile()));
	}

	@Test
	public void testGenerateMatcherFor_FileHasBeanCreated_CreatedJavaFileShouldBeCompilableWithoutAnyIssues()
			throws Exception {
		// Preparation
		Class<SimplePojo> type = SimplePojo.class;

		// Execution
		classUnderTest.generateMatcherFor(type, srcOutputDir);

		// Assertion
		CompilationResult result = compileGeneratedSourceFileFor(type);
		assertThat("Compilation Result", result, new BeanPropertyMatcher<CompilationResult>(CompilationResult.class)
				.with("errors", is(emptyArray())).with("warnings", is(emptyArray())));
	}

	@Test
	public void testGenerateMatcherFor_FileHasBeanCreated_ShouldBeAbleToLoadAndInstanciateGeneratedClass()
			throws Exception {
		// Preparation
		Class<SimplePojo> type = SimplePojo.class;

		// Execution
		classUnderTest.generateMatcherFor(type, srcOutputDir);

		// Assertion
		loadInstanceOfGeneratedClassFor(type);
	}

	private Object loadInstanceOfGeneratedClassFor(final Class<SimplePojo> type)
			throws IOException, Exception, InstantiationException, IllegalAccessException {
		compileGeneratedSourceFileFor(type);
		Class<?> loadClass = loadClassFor(type);
		return loadClass.newInstance();
	}

	private Class<?> loadClassFor(final Class<?> type) throws Exception {
		return classLoader.loadClass(generatedFullQualifiedClassNameFor(type));
	}

	private String generatedFullQualifiedClassNameFor(final Class<?> type) {
		return getPackageNameOf(type) + "." + generatedMatcherClassNameFor(type);
	}

	private CompilationResult compileGeneratedSourceFileFor(final Class<SimplePojo> type) throws IOException {
		String[] pResourcePaths = { getGeneratedRelativePathOfUnixString(type) };
		FileResourceReader sourceFolderResource = new FileResourceReader(srcOutputDir.toFile());
		FileResourceStore classFolderResource = new FileResourceStore(classOutputDir.toFile());

		return compiler.compile(pResourcePaths, sourceFolderResource, classFolderResource, getClass().getClassLoader(),
				compilerSettings);
	}

	private String getGeneratedRelativePathOfUnixString(final Class<SimplePojo> type) {
		return getGeneratedRelativePathOf(type).toString().replaceAll("\\\\", "/");
	}

	private File generatedSourceFileFor(final Class<?> type) {
		return generatedSourcePathFor(type).toFile();
	}

	private Path generatedSourcePathFor(final Class<?> type) {
		return srcOutputDir.resolve(getGeneratedRelativePathOf(type));
	}

	private Path getGeneratedRelativePathOf(final Class<?> type) {
		return getPackagePath(type).resolve(generatedMatcherJavaFileNameFor(type));
	}

	private String generatedMatcherJavaFileNameFor(final Class<?> type) {
		return generatedMatcherClassNameFor(type) + JAVA_FILE_POSTFIX;
	}

	private String generatedMatcherClassNameFor(final Class<?> type) {
		return type.getSimpleName() + MATCHER_POSTFIX;
	}

	private Path getPackagePath(final Class<?> type) {
		return Paths.get(getPackageNameOf(type).replaceAll("\\.", "/"));
	}

	private String getPackageNameOf(final Class<?> type) {
		return type.getPackage().getName();
	}

	@Value
	public class SimplePojo {
		private String simpleProp;
	}

}