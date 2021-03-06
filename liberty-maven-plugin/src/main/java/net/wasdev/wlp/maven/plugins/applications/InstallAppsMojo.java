/**
 * (C) Copyright IBM Corporation 2014, 2017.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.wasdev.wlp.maven.plugins.applications;

import java.io.File;
import java.text.MessageFormat;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.pluginsupport.util.ArtifactItem;

import net.wasdev.wlp.maven.plugins.ApplicationXmlDocument;

/**
 * Copy applications to the specified directory of the Liberty server.
 */
@Mojo(name = "install-apps", requiresDependencyResolution=ResolutionScope.COMPILE)
public class InstallAppsMojo extends InstallAppMojoSupport {
    
    protected void doExecute() throws Exception {
        if (skip) {
            return;
        }
        checkServerHomeExists();
        checkServerDirectoryExists();
        
        // update target server configuration
        copyConfigFiles();
        exportParametersToXml();
        
        boolean installDependencies = false;
        boolean installProject = false;
        
        switch (installAppPackages) {
            case "all":
                installDependencies = true;
                installProject = true;
                break;
            case "dependencies":
                installDependencies = true;
                break;
            case "project":
                installProject = true;
                break;
            default:
                return;
        }
        if (installDependencies) {
            installDependencies();
        }
        if (installProject) {
            installProject();
        }
        
        // create application configuration in configDropins if it is not configured
        if (applicationXml.hasChildElements()) {
            log.warn(messages.getString("warn.install.app.add.configuration"));
            applicationXml.writeApplicationXmlDocument(serverDirectory);
        } else {
            if (ApplicationXmlDocument.getApplicationXmlFile(serverDirectory).exists()) {
                ApplicationXmlDocument.getApplicationXmlFile(serverDirectory).delete();
            }
        }
    }
    
    private void installDependencies() throws Exception {
        @SuppressWarnings("unchecked")
        Set<Artifact> artifacts = (Set<Artifact>) project.getDependencyArtifacts();
        for (Artifact dep : artifacts) {
            // skip if not an application type supported by Liberty
            if (!isSupportedType(dep.getType())) {
                continue;
            }
            // skip assemblyArtifact if specified as a dependency
            if (assemblyArtifact != null && matches(dep, assemblyArtifact)) {
                continue;
            }
            if (dep.getScope().equals("compile")) {
                installApp(dep);
            }
        }
    }
    
    private void installProject() throws Exception {
        if (isSupportedType(project.getPackaging())) {
            if (looseApplication) {
                switch(project.getPackaging()) {
                    case "war":
                        installLooseConfigApp();
                        break;
                    case "liberty-assembly":
                        File dir = getWarSourceDirectory();
                        if (dir.exists()) {
                            installLooseConfigApp();
                        } else {
                            log.debug("liberty-assembly project does not have source code for web project.");
                        }
                        break;
                    default:
                        log.info(MessageFormat.format(messages.getString("info.loose.application.not.supported"),
                                project.getPackaging()));
                        installApp(project.getArtifact());
                        break;
                }
            } else {
                installApp(project.getArtifact());
            }
        } else {
            throw new MojoExecutionException(MessageFormat.format(messages.getString("error.application.not.supported"),
                    project.getId()));
        }
    }

    private boolean matches(Artifact dep, ArtifactItem assemblyArtifact) {
        return dep.getGroupId().equals(assemblyArtifact.getGroupId())
                && dep.getArtifactId().equals(assemblyArtifact.getArtifactId())
                && dep.getType().equals(assemblyArtifact.getType());
    }
    
    private boolean isSupportedType(String type) {
        switch (type) {
        case "ear":
        case "war":
        case "eba":
        case "esa":
        case "liberty-assembly":
            return true;
        default:
            return false;
        }
    }
   
}
