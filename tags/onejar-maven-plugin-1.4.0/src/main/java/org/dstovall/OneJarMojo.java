package org.dstovall;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Creates an executable one-jar version of the project's normal jar, including all dependencies.
 *
 * @goal one-jar
 * @phase package
 * @requiresProject
 * @requiresDependencyResolution runtime
 */
public class OneJarMojo extends AbstractMojo {

    /**
     * All the dependencies including trancient dependencies.
     *
     * @parameter default-value="${project.artifacts}"
     * @required
     * @readonly
     */
    private Collection<Artifact> artifacts;
    
    /**
     * FileSet to be included in the "binlib" directory inside the one-jar. This is the place to include native
     * libraries such as .dll files and .so files. They will automatically be loaded by the one-jar.
     * @parameter
     */
    private FileSet[] binlibs;

    /**
     * The directory for the resulting file.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private File outputDirectory;

    /**
     * Name of the main JAR.
     *
     * @parameter expression="${project.build.finalName}.jar"
     * @readonly
     * @required
     */
    private String mainJarFilename;

    /**
     * Implementation Version of the jar.  Defaults to the build's version.
     *
     * @parameter expression="${project.version}"
     * @required
     */
    private String implementationVersion;

    /**
     * Name of the generated JAR.
     *
     * @parameter expression="${project.build.finalName}.one-jar.jar"
     * @required
     */
    private String filename;

    /**
     * The version of one-jar to use.  Has a default, so typically no need to specify this.
     *
     * @parameter expression="${onejar-version}" default-value="0.96"
     */
    private String onejarVersion;

    /**
     * Whether to attach the generated one-jar to the build. You may also wish to set <code>classifier</code>.
     *
     * @parameter default-value=false
     */
    private boolean attachToBuild;

    /**
     * Classifier to use, if the one-jar is to be attached to the build.
     * Set <code>&lt;attachToBuild&gt;true&lt;/attachToBuild&gt; if you want that.
     *
     * @parameter default-value="onejar" 
     */
    private String classifier;

    /**
     * This Maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * For attaching artifacts etc.
     *
     * @component
     * @readonly
     */
    private MavenProjectHelper projectHelper;

    /**
     * The main class that one-jar should activate
     *
     * @parameter expression="${onejar-mainclass}"
     */
    private String mainClass;

    public void execute() throws MojoExecutionException {

        // Show some info about the plugin.
        displayPluginInfo();

        JarOutputStream out = null;
        JarInputStream template = null;

        File onejarFile;
        try {
            // Create the target file
            onejarFile = new File(outputDirectory, filename);

            // Open a stream to write to the target file
            out = new JarOutputStream(new FileOutputStream(onejarFile, false), getManifest());

            // Main jar
            if (getLog().isDebugEnabled()) {
                getLog().debug("Adding main jar main/[" + mainJarFilename + "]");
            }
            addToZip(new File(outputDirectory, mainJarFilename), "main/", out);

            // Libraries
            List<File> jars = getFilesForStrings(artifacts, false, true);
            if (getLog().isDebugEnabled()) {
                getLog().debug("Adding [" + jars.size() + "] libaries...");
            }
            for (File jar : jars) {
                addToZip(jar, "lib/", out);
            }

            // Native libraries
            if (binlibs != null) {
                for (FileSet eachFileSet : binlibs) {
                    List<File> includedFiles = toFileList(eachFileSet);
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("Adding [" + includedFiles.size() + "] native libaries...");
                    }
                    for (File eachIncludedFile : includedFiles) {
                        addToZip(eachIncludedFile, "binlib/", out);
                    }
                }
            }

            // One-jar stuff
            getLog().debug("Adding one-jar components...");
            template = openOnejarTemplateArchive();
            ZipEntry entry;
            while ((entry = template.getNextEntry()) != null) {
                // Skip the manifest file, no need to clutter...
                if (!"boot-manifest.mf".equals(entry.getName())) {
                    addToZip(out, entry, template);
                }
            }

        } catch (IOException e) {
            getLog().error(e);
            throw new MojoExecutionException("One-jar Mojo failed.", e);
        } finally {
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(template);
        }

        // Attach the created one-jar to the build.
        if (attachToBuild){
            projectHelper.attachArtifact(project, "jar", classifier, onejarFile);
        }
    }

    private void displayPluginInfo() {
        getLog().info("Using One-Jar to create a single-file distribution");
        getLog().info("Implementation Version: " + implementationVersion);
        getLog().info("Using One-Jar version: " + onejarVersion);
        getLog().info("More info on One-Jar: http://one-jar.sourceforge.net/");
        getLog().info("License for One-Jar:  http://one-jar.sourceforge.net/one-jar-license.txt");
        getLog().info("One-Jar file: " + outputDirectory.getAbsolutePath() + File.separator + filename);
    }

    // ----- One-Jar Template ------------------------------------------------------------------------------------------

    private String getOnejarArchiveName() {
        return "one-jar-boot-" + onejarVersion + ".jar";
    }

    private JarInputStream openOnejarTemplateArchive() throws IOException {
        return new JarInputStream(getClass().getClassLoader().getResourceAsStream(getOnejarArchiveName()));
    }

    private Manifest getManifest() throws IOException {
        // Copy the template's boot-manifest.mf file
        ZipInputStream zipIS = openOnejarTemplateArchive();
        Manifest manifest = new Manifest(getFileBytes(zipIS, "boot-manifest.mf"));
        IOUtils.closeQuietly(zipIS);

        // If the client has specified a mainClass argument, add the proper entry to the manifest
        if (mainClass != null) {
            manifest.getMainAttributes().putValue("One-Jar-Main-Class", mainClass);
        }

        // If the client has specified an implementationVersion argument, add it also
        // (It's required and defaulted, so this always executes...)
        //
        // TODO: The format of this manifest entry is not "hard and fast".  Some specs call for "implementationVersion",
        // some for "implemenation-version", and others use various capitalizations of these two.  It's likely that a
        // better solution then this "brute-force" bit here is to allow clients to configure these entries from the
        // Maven POM.
        if (implementationVersion != null) {
            manifest.getMainAttributes().putValue("ImplementationVersion", implementationVersion);
        }

        return manifest;
    }

    // ----- Zip-file manipulations ------------------------------------------------------------------------------------

    private void addToZip(File sourceFile, String zipfilePath, JarOutputStream out) throws IOException {
        addToZip(out, new ZipEntry(zipfilePath + sourceFile.getName()), new FileInputStream(sourceFile));
    }

    private void addToZip(JarOutputStream out, ZipEntry entry, InputStream in) throws IOException {
        out.putNextEntry(entry);
        IOUtils.copy(in, out);
        out.closeEntry();
    }

    private InputStream getFileBytes(ZipInputStream is, String name) throws IOException {
        ZipEntry entry = null;
        while ((entry = is.getNextEntry()) != null) {
            if (entry.getName().equals(name)) {
                byte[] data = IOUtils.toByteArray(is);
                return new ByteArrayInputStream(data);
            }
        }
        return null;
    }

    /**
     * Returns File objects for each parameter string. TODO: Replace with Transformer
     *
     * @param artifacts          <em>(Must be non-<code>null</code>)</em>
     * @param includeDirectories
     * @param includeFiles
     * @return <em>(Must be non-<code>null</code>)</em>
     */
    private List<File> getFilesForStrings(Collection<Artifact> artifacts, boolean includeDirectories, boolean includeFiles) {
        List<File> files = new ArrayList<File>(artifacts.size());

        for (Artifact artifact : artifacts) {
            File file = artifact.getFile();

            if (file.isFile() && includeFiles) {
                files.add(file);
            }

            if (file.isDirectory() && includeDirectories) {
                files.add(file);
            }
        }
        return files;
    }

    private static List<File> toFileList(FileSet fileSet)
            throws IOException {
        File directory = new File(fileSet.getDirectory());
        String includes = toString(fileSet.getIncludes());
        String excludes = toString(fileSet.getExcludes());
        return FileUtils.getFiles(directory, includes, excludes);
    }

    private static String toString(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        for (String string : strings) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(string);
        }
        return sb.toString();
    }

}
