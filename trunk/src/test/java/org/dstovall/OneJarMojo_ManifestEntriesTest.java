package org.dstovall;

import java.io.File;
import java.util.HashMap;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Test;

public class OneJarMojo_ManifestEntriesTest {

	/**
	 * we might want to use more sensible tests,
	 * but for now I would just like to see whether new changes
	 * break older options.
	 */
	
	@Test
	public void whenManifestEntriesSpecifiedThenNoFailure() throws Exception {
		MojoUnderTest mojo = new MojoUnderTest();
		mojo.set("manifestEntries", new HashMap<String, String>());
		mojo.execute();
	}
	
	@Test
	public void whenManifestEntriesMissingThenNoFailure() throws Exception {
		MojoUnderTest mojo = new MojoUnderTest();
		mojo.execute();
	}

	class MojoUnderTest extends OneJarMojo {
		public MojoUnderTest() {
			String defaultFinalName = "default-finalname";
			File tempOutput = new File(System.getProperty("java.io.tmpdir")+"/one-jar/");
			tempOutput.mkdirs();
			tempOutput.deleteOnExit();
			
			// setting required field that have defaults
			set("outputDirectory", tempOutput);
			set("filename", defaultFinalName);
			set("mainJarFilename", defaultFinalName);
			set("onejarVersion", "0.97");
		}
		
		void set(String optionName, Object value) {
			try {
				ReflectionUtils.setVariableValueInObject(this, optionName, value);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}
	}
	
}
