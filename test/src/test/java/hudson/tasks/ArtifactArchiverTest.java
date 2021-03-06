/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.tasks;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.tasks.LogRotatorTest.TestsFail;
import static hudson.tasks.LogRotatorTest.build;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.util.VirtualFile;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Verifies that artifacts from the last successful and stable builds of a job will be kept if requested.
 */
public class ArtifactArchiverTest {
    
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void testSuccessVsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", true, false)));
        assertEquals("(no artifacts)", Result.FAILURE, build(project)); // #1
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #2
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        project.getBuildersList().replaceBy(Arrays.asList(new CreateArtifact(), new FailureBuilder()));
        assertEquals(Result.FAILURE, build(project)); // #3
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #4
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #5
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse("no better than #4", project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #6
        assertTrue("#2 is still lastSuccessful until #6 is complete", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #7
        assertFalse("lastSuccessful was #6 for ArtifactArchiver", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertFalse(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertTrue(project.getBuildByNumber(7).getHasArtifacts());
    }

    @Test
    @Bug(2417)
    public void testStableVsUnstable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Publisher artifactArchiver = new ArtifactArchiver("f", "", true, false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #1
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        project.getPublishersList().replaceBy(Arrays.asList(artifactArchiver, new TestsFail()));
        assertEquals(Result.UNSTABLE, build(project)); // #2
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertEquals(Result.UNSTABLE, build(project)); // #3
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertEquals(Result.UNSTABLE, build(project)); // #4
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        assertEquals(Result.SUCCESS, build(project)); // #5
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #6
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
    }

    @Test
    @Bug(3227)
    public void testEmptyDirectories() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        Publisher artifactArchiver = new ArtifactArchiver("dir/", "", false, false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath dir = build.getWorkspace().child("dir");
                dir.child("subdir1").mkdirs();
                FilePath subdir2 = dir.child("subdir2");
                subdir2.mkdirs();
                subdir2.child("file").write("content", "UTF-8");
                return true;
            }
        }));
        assertEquals(Result.SUCCESS, build(project)); // #1
        File artifacts = project.getBuildByNumber(1).getArtifactsDir();
        File[] kids = artifacts.listFiles();
        assertEquals(1, kids.length);
        assertEquals("dir", kids[0].getName());
        kids = kids[0].listFiles();
        assertEquals(1, kids.length);
        assertEquals("subdir2", kids[0].getName());
        kids = kids[0].listFiles();
        assertEquals(1, kids.length);
        assertEquals("file", kids[0].getName());
    }

    @Test
    @Bug(10502)
    public void testAllowEmptyArchive() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", false, true)));
        assertEquals("(no artifacts)", Result.SUCCESS, build(project));
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
    }

    @Bug(21958)
    @Test public void symlinks() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                if (ws == null) {
                    return false;
                }
                FilePath dir = ws.child("dir");
                dir.mkdirs();
                dir.child("fizz").write("contents", null);
                dir.child("lodge").symlinkTo("fizz", listener);
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("dir/lodge", "", false, true));
        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FilePath ws = b.getWorkspace();
        assertNotNull(ws);
        assumeTrue("May not be testable on Windows:\n" + JenkinsRule.getLog(b), ws.child("dir/lodge").exists());
        List<FreeStyleBuild.Artifact> artifacts = b.getArtifacts();
        assertEquals(1, artifacts.size());
        FreeStyleBuild.Artifact artifact = artifacts.get(0);
        assertEquals("dir/lodge", artifact.relativePath);
        VirtualFile[] kids = b.getArtifactManager().root().child("dir").list();
        assertEquals(1, kids.length);
        assertEquals("lodge", kids[0].getName());
        // do not check that it .exists() since its target has not been archived
    }
    
    private void runNewBuildAndStartUnitlIsCreated(AbstractProject project) throws InterruptedException{
        int buildNumber = project.getNextBuildNumber();
        project.scheduleBuild2(0);
        int count = 0;
        while(project.getBuildByNumber(buildNumber)==null && count<50){
            Thread.sleep(100);
            count ++;
        }
        if(project.getBuildByNumber(buildNumber)==null)
            fail("Build " + buildNumber + " did not created.");
    }
    
    @Test
    public void testPrebuildWithConcurrentBuilds() throws IOException, Exception{
        FreeStyleProject project = j.createFreeStyleProject();
        j.jenkins.setNumExecutors(4);
        //logest build
        project.getBuildersList().add(new Shell("sleep 100"));
        project.setConcurrentBuild(true);
        Publisher artifactArchiver = new ArtifactArchiver("dir/", "", true, false);
        runNewBuildAndStartUnitlIsCreated(project);
        //shortest build
        project.getBuildersList().clear();
        j.buildAndAssertSuccess(project);
        //longest build
        project.getBuildersList().add(new Shell("sleep 100"));
        runNewBuildAndStartUnitlIsCreated(project);
        AbstractBuild build = project.getLastBuild();
        BuildListener listner = new StreamBuildListener(BuildListener.NULL.getLogger(), Charset.defaultCharset());
        try{
            System.out.println("last build is " + project.getLastBuild());
            for(AbstractBuild b: project.getBuilds()){
                System.out.println(" build " + b + " sttus " + b.getResult());
            }
            boolean ok = artifactArchiver.prebuild(build, listner);
            assertTrue("Artefact archiver should not have any problem.", ok);
        }
        catch(Exception e){
            fail("Artefact archiver should not throw exception " + e + " for concurrent builds");
        }
                
    }

    static class CreateArtifact extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child("f").write("content", "UTF-8");
            return true;
        }
    }

    static class CreateArtifactAndFail extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            build.getWorkspace().child("f").write("content", "UTF-8");
            throw new AbortException("failing the build");
        }
    }

    @Test
    @Bug(22698)
    public void testArchivingSkippedWhenOnlyIfSuccessfulChecked() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", false, false, false)));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifactAndFail()));
        assertEquals(Result.FAILURE, build(project));
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", false, false, true)));
        assertEquals(Result.FAILURE, build(project));
        assertTrue(project.getBuildByNumber(1).getHasArtifacts());
        assertFalse(project.getBuildByNumber(2).getHasArtifacts());
    }




    static class CreateDefaultExcludesArtifact extends TestBuilder {
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
            FilePath dir = build.getWorkspace().child("dir");
            FilePath subSvnDir = dir.child(".svn");
            subSvnDir.mkdirs();
            subSvnDir.child("file").write("content", "UTF-8");

            FilePath svnDir = build.getWorkspace().child(".svn");
            svnDir.mkdirs();
            svnDir.child("file").write("content", "UTF-8");

            dir.child("file").write("content", "UTF-8");
            return true;
        }
    }

    @Test
    @Bug(20086)
    public void testDefaultExcludesOn() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        Publisher artifactArchiver = new ArtifactArchiver("**", "", false, false, true, true);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateDefaultExcludesArtifact()));

        assertEquals(Result.SUCCESS, build(project)); // #1
        VirtualFile artifacts = project.getBuildByNumber(1).getArtifactManager().root();
        assertFalse(artifacts.child(".svn").child("file").exists());
        assertFalse(artifacts.child("dir").child(".svn").child("file").exists());

    }

    @Test
    @Bug(20086)
    public void testDefaultExcludesOff() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        Publisher artifactArchiver = new ArtifactArchiver("**", "", false, false, true, false);
        project.getPublishersList().replaceBy(Collections.singleton(artifactArchiver));
        project.getBuildersList().replaceBy(Collections.singleton(new CreateDefaultExcludesArtifact()));

        assertEquals(Result.SUCCESS, build(project)); // #1
        VirtualFile artifacts = project.getBuildByNumber(1).getArtifactManager().root();
        assertTrue(artifacts.child(".svn").child("file").exists());
        assertTrue(artifacts.child("dir").child(".svn").child("file").exists());
    }
}
