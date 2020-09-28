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

import net.adamcin.oakpal.api.Fun;
import net.adamcin.oakpal.api.PathAction;
import net.adamcin.oakpal.api.Result;
import net.adamcin.oakpal.api.SimpleProgressCheck;
import org.apache.jackrabbit.vault.packaging.PackageId;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Clientlibs extends SimpleProgressCheck {
    private static final String PROP_ALLOW_PROXY = "allowProxy";
    private static final String PROP_JS_PROCESSOR = "jsProcessor";

    private final Map<String, PackageId> myClientlibs = new LinkedHashMap<>();

    @Override
    public void importedPath(final PackageId packageId, final String path, final Node node, final PathAction action)
            throws RepositoryException {
        if (path.startsWith("/apps/classic-app/clientlibs/")
                && node.isNodeType("{http://www.day.com/jcr/cq/1.0}ClientLibraryFolder")) {
            myClientlibs.put(path, packageId);
            if (!node.hasProperty(PROP_ALLOW_PROXY)
                    || node.getProperty(PROP_ALLOW_PROXY).getType() != PropertyType.BOOLEAN) {
                reporting(builder -> builder
                        .withPackage(packageId)
                        .withDescription(path + ": clientlib does not set allowProxy"));
            }

        }
    }

    @Override
    public void afterScanPackage(final PackageId scanPackageId, final Session inspectSession)
            throws RepositoryException {
        for (Map.Entry<String, PackageId> clientlib : myClientlibs.entrySet()) {
            final String path = clientlib.getKey();
            final PackageId packageId = clientlib.getValue();
            if (inspectSession.nodeExists(path)) {
                final Node node = inspectSession.getNode(path);
                if (node.hasNode("js.txt")) {
                    if (!node.hasProperty(PROP_JS_PROCESSOR)
                            || !node.getProperty(PROP_JS_PROCESSOR).isMultiple()
                            || node.getProperty(PROP_JS_PROCESSOR).getType() != PropertyType.STRING) {
                        reporting(builder -> builder
                                .withPackage(packageId)
                                .withDescription(path + ": clientlib does not specify a String[] jsProcessor property"));
                    } else {
                        final Result<List<String>> propertyResult = Stream
                                .of(node.getProperty(PROP_JS_PROCESSOR).getValues())
                                .map(Fun.result1(Value::getString))
                                .collect(Result.tryCollect(Collectors.toList()));
                        propertyResult.throwCause(RepositoryException.class);
                        propertyResult.forEach(values -> validateJsProcessor(packageId, path, values));
                    }
                }
            }
        }

        myClientlibs.clear();
    }

    void validateJsProcessor(final PackageId packageId, final String path, final List<String> values) {
        boolean minSpecified = false;
        for (String value : values) {
            if (value.startsWith("min:")) {
                minSpecified = true;
                if (value.startsWith("min:gcc")
                        && !value.contains(";languageIn=ECMASCRIPT6")) {
                    reporting(builder -> builder
                            .withPackage(packageId)
                            .withDescription(path + ": jsProcessor must specify [min:none] or [min:gcc;languageIn=ECMASCRIPT6]"));
                }
            }
        }
        if (!minSpecified) {
            reporting(builder -> builder
                    .withPackage(packageId)
                    .withDescription(path + ": jsProcessor must specify [min:none] or [min:gcc;languageIn=ECMASCRIPT6]"));
        }
    }
}
