package com.marklogic.maven;

import com.marklogic.xcc.AdhocQuery;
import com.marklogic.xcc.Content;
import com.marklogic.xcc.Session;
import com.marklogic.xcc.exceptions.RequestException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.marklogic.xcc.ContentFactory.newContent;

/**
 * @author Bob Browning <bob.browning@pressassociation.com>
 */
public abstract class AbstractInstallMojo extends AbstractDeploymentMojo {

    protected static final String ACTION_INSTALL_CONTENT = "install-content";
    protected static final String ACTION_INSTALL_CPF = "install-cpf";
    protected static final String ACTION_INSTALL_DATABASES = "install-databases";
    protected static final String ACTION_INSTALL_TRIGGERS = "install-triggers";
    protected static final String ACTION_INSTALL_SERVERS = "install-servers";

    protected void installContent() throws MojoExecutionException {
        executeAction(ACTION_INSTALL_CONTENT);

        if(getCurrentEnvironment().getResources() != null) {
            /*
             * Install content resources from maven project
             */
            installResources(getCurrentEnvironment().getResources());
        }
    }

    protected void installCPF() throws MojoExecutionException {
        if(getCurrentEnvironment().getPipelineResources() != null) {
            /*
             * Install pipeline resources from maven project
             */
            installPipeline(getCurrentEnvironment().getPipelineResources());
        }

        executeAction(ACTION_INSTALL_CPF);
    }

    protected void installDatabases() throws MojoExecutionException {
        executeAction(ACTION_INSTALL_DATABASES);
    }

    protected void installTriggers() throws MojoExecutionException {
        executeAction(ACTION_INSTALL_TRIGGERS);
    }

    protected void installServers() throws MojoExecutionException {
        executeAction(ACTION_INSTALL_SERVERS);
    }


    private void installPipeline(ResourceFileSet[] resources) {
         try {
            FileSetManager manager = new FileSetManager();

            for (ResourceFileSet resource : resources) {
                final String targetDatabase = getCurrentEnvironment().getApplicationName() + "-" + resource.getDatabase();
                getLog().info(" -- ".concat(targetDatabase).concat(" -- "));

                /*
                 * Get connection to database for uploading content
                 */
                Session session = getSession(targetDatabase);

                AdhocQuery query = session.newAdhocQuery("(::)\n" +
                        "xquery version \"1.0-ml\";\n" +
                        "import module namespace p = \"http://marklogic.com/cpf/pipelines\" at \"/MarkLogic/cpf/pipelines.xqy\";\n" +
                        "declare variable $file as xs:string external; \n" +
                        "p:insert( xdmp:unquote($file)/* )\n" +
                        "(::)");

                for (String f : manager.getIncludedFiles(resource)) {
                    File sourceFile = new File(resource.getDirectory(), f);
                    getLog().info(String.format("Loading pipeline configuration %s", sourceFile.getPath()));
                    try {
                        query.setNewStringVariable("file", getFileAsString(sourceFile));
                        session.submitRequest(query);
                    } catch (IOException e) {
                        getLog().error("Failed to read pipeline file ".concat(f), e);
                    } catch (RequestException e) {
                        getLog().error("Failed to insert pipeline file ".concat(f), e);
                    }
                }
            }
        } finally {
            for ( Map.Entry<String, Session> e : sessions.entrySet() ) {
                e.getValue().close();
            }
            sessions = new HashMap<String, Session>();
        }
    }

    private void installResources(ResourceFileSet[] resources) {
        try {
            FileSetManager manager = new FileSetManager();

            for (ResourceFileSet resource : resources) {
                final String targetDatabase = getCurrentEnvironment().getApplicationName() + "-" + resource.getDatabase();
                getLog().info(" -- ".concat(targetDatabase).concat(" -- "));

                /*
                 * Get connection to database for uploading content
                 */
                Session session = getSession(targetDatabase);

                for (String f : manager.getIncludedFiles(resource)) 
                {
                	File sourceFile = new File(resource.getDirectory(), f);
                	File destinationFile = new File(resource.getOutputDirectory(), f);

                	String destinationPath = destinationFile.getPath().replace(File.separatorChar, '/');
                	getLog().info(String.format("Deploying %s to %s", sourceFile.getPath(), destinationPath));

                	try 
                	{
                		Content c = newContent(destinationPath, getFileAsString(sourceFile), null);
                		session.insertContent(c);
                    } 
                	catch (IOException e) {
                        getLog().error("Failed to read content file ".concat(f), e);
                    } catch (RequestException e) {
                        getLog().error("Failed to insert content file ".concat(f), e);
                    }
                }
            }
        } finally {
            for ( Map.Entry<String, Session> e : sessions.entrySet() ) {
                e.getValue().close();
            }
            sessions = new HashMap<String, Session>();
        }
    }
}