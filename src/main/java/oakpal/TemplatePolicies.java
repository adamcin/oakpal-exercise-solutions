/*
 * Copyright 2020 Mark Adamcin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package oakpal;

import net.adamcin.oakpal.api.PathAction;
import net.adamcin.oakpal.api.Severity;
import net.adamcin.oakpal.api.SimpleProgressCheck;
import org.apache.jackrabbit.vault.packaging.PackageId;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.util.TraversingItemVisitor;
import java.util.LinkedHashMap;
import java.util.Map;

public class TemplatePolicies extends SimpleProgressCheck {
    private static final String NS_CQ = "{http://www.day.com/jcr/cq/1.0}";
    private static final String NS_SLING = "{http://sling.apache.org/jcr/sling/1.0}";
    private static final String SETTINGS_WCM = "/settings/wcm";

    private final Map<String, PackageId> templatePaths = new LinkedHashMap<>();

    @Override
    public void importedPath(final PackageId packageId, final String path, final Node node, final PathAction action)
            throws RepositoryException {
        if (path.startsWith("/conf/classic-app")
                && path.contains(SETTINGS_WCM)
                && node.isNodeType(NS_CQ + "Template")) {
            templatePaths.put(path, packageId);
        }
    }

    Node resolvePolicyNode(final String policiesBase, final String path, final Session session)
            throws RepositoryException {
        if (path.isEmpty()) {
            return null;
        }
        final String absPath = path.startsWith("/") ? path : policiesBase + "/" + path;
        if (session.nodeExists(absPath)) {
            return session.getNode(absPath);
        } else {
            return null;
        }
    }

    Node resolvePolicyNode(final String policiesBase, final Node mappingNode) throws RepositoryException {
        if (mappingNode.hasProperty(NS_CQ + "policy")) {
            final String policyPath = mappingNode.getProperty(NS_CQ + "policy").getString();
            return resolvePolicyNode(policiesBase, policyPath, mappingNode.getSession());
        }
        return null;
    }

    void recurseCheckCqPolicy(final PackageId packageId, final String policiesBase, final Node node)
            throws RepositoryException {
        if (node.hasProperty(NS_CQ + "policy")) {
            final String policyPath = node.getProperty(NS_CQ + "policy").getString();
            final Node policyNode = resolvePolicyNode(policiesBase, policyPath, node.getSession());
            if (policyNode == null) {
                final String mappingPath = node.getPath();
                reporting(builder -> builder
                        .withSeverity(Severity.MINOR)
                        .withPackage(packageId)
                        .withDescription(mappingPath + ": failed to resolve cq:policy path " + policyPath));
            }
        }
        if (node.hasNodes()) {
            for (NodeIterator children = node.getNodes(); children.hasNext(); ) {
                final Node child = children.nextNode();
                recurseCheckCqPolicy(packageId, policiesBase, child);
            }
        }
    }

    Node resolveResourceType(final Node resourceNode) throws RepositoryException {
        if (resourceNode.hasProperty(NS_SLING + "resourceType")) {
            final String resourceType = resourceNode.getProperty(NS_SLING + "resourceType").getString();
            final String absResourceType = resourceType.startsWith("/") ? resourceType : "/apps/" + resourceType;
            if (resourceNode.getSession().nodeExists(absResourceType)) {
                return resourceNode.getSession().getNode(absResourceType);
            }
        }
        return null;
    }

    class TemplateResourceVisitor extends TraversingItemVisitor {
        private final PackageId packageId;
        private final Node templatePage;
        private final Node mappingsPage;
        private final String policiesBasePath;

        public TemplateResourceVisitor(final PackageId packageId,
                                       final Node templatePage,
                                       final Node mappingsPage,
                                       final String policiesBasePath) {
            super(false);
            this.packageId = packageId;
            this.templatePage = templatePage;
            this.mappingsPage = mappingsPage;
            this.policiesBasePath = policiesBasePath;
        }

        @Override
        protected void entering(final Property property, final int level) throws RepositoryException {
            /* do nothing */
        }

        @Override
        protected void leaving(final Property property, final int level) throws RepositoryException {
            /* do nothing */
        }

        protected String resourceRelPath(final Node node) throws RepositoryException {
            final String basePath = templatePage.getPath() + "/";
            final String path = node.getPath();
            return path.substring(basePath.length());
        }

        protected boolean isContainer(final Node resourceType) throws RepositoryException {
            if (resourceType.hasProperty(NS_CQ + "isContainer")) {
                return resourceType.getProperty(NS_CQ + "isContainer").getBoolean()
                        || "true".equals(resourceType.getProperty(NS_CQ + "isContainer").getString());
            }
            return false;
        }

        @Override
        protected void entering(final Node node, final int level) throws RepositoryException {
            Node resourceType = resolveResourceType(node);
            if (resourceType != null) {
                if (isContainer(resourceType) && !node.hasNodes()) {
                    final String relPath = resourceRelPath(node);
                    final String templatePath = templatePage.getPath();
                    if (!mappingsPage.hasNode(relPath)) {
                        reporting(builder -> builder
                                .withSeverity(Severity.MINOR)
                                .withPackage(packageId)
                                .withDescription(String.format("%s: failed to find policy mapping for empty container in template %s",
                                        templatePath, relPath)));
                    } else {
                        final Node policyNode = resolvePolicyNode(policiesBasePath, mappingsPage.getNode(relPath));
                        if (policyNode == null) {
                            reporting(builder -> builder
                                    .withSeverity(Severity.MINOR)
                                    .withPackage(packageId)
                                    .withDescription(String.format("%s missing policy for empty container in template %s",
                                            templatePath, relPath)));
                        } else {
                            if (!policyNode.hasProperty("components")) {
                                reporting(builder -> builder
                                        .withSeverity(Severity.MINOR)
                                        .withPackage(packageId)
                                        .withDescription(String.format("%s: Allowed Components missing in policy for empty container in template %s",
                                                templatePath, relPath)));
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void leaving(final Node node, final int level) throws RepositoryException {
            /* do nothing */
        }
    }

    void validateTemplatePolicies(final PackageId packageId, final String templatePath, final Session inspectSession)
            throws RepositoryException {
        final Node templateNode = inspectSession.getNode(templatePath);
        if (templateNode.hasNode("policies/jcr:content")) {
            final Node mappingsPage = templateNode.getNode("policies/jcr:content");
            final String wcmBasePath = templatePath.substring(0, templatePath.indexOf(SETTINGS_WCM) + SETTINGS_WCM.length());
            final String policiesBasePath = wcmBasePath + "/policies";
            if (!inspectSession.nodeExists(policiesBasePath)) {
                reporting(builder -> builder
                        .withSeverity(Severity.MINOR)
                        .withPackage(packageId)
                        .withDescription(String.format("%s: failed to find policies page in settings %s",
                                templatePath, policiesBasePath)));
            } else {
                recurseCheckCqPolicy(packageId, policiesBasePath, mappingsPage);
            }
            if (templateNode.hasNode("initial/jcr:content")) {
                final Node templatePage = templateNode.getNode("initial/jcr:content");
                templatePage.accept(new TemplateResourceVisitor(packageId, templatePage,
                        mappingsPage, policiesBasePath));
            }
            if (templateNode.hasNode("structure/jcr:content")) {
                final Node templatePage = templateNode.getNode("structure/jcr:content");
                templatePage.accept(new TemplateResourceVisitor(packageId, templatePage,
                        mappingsPage, policiesBasePath));
            }
        }
    }

    @Override
    public void afterScanPackage(final PackageId scanPackageId, final Session inspectSession)
            throws RepositoryException {
        for (Map.Entry<String, PackageId> templatePage : templatePaths.entrySet()) {
            final String templatePath = templatePage.getKey();
            final PackageId packageId = templatePage.getValue();
            validateTemplatePolicies(packageId, templatePath, inspectSession);
        }
        templatePaths.clear();
    }
}
