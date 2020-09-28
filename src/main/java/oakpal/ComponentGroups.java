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
import net.adamcin.oakpal.api.SimpleProgressCheck;
import org.apache.jackrabbit.vault.packaging.PackageId;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ComponentGroups extends SimpleProgressCheck {

    private final Set<String> formGroups = Stream.of(".hidden", "AEM Classic App - Form").collect(Collectors.toSet());
    private final Set<String> validGroups = Stream.of(
            ".hidden",
            "AEM Classic App - Content",
            "AEM Classic App - Structure",
            "AEM Classic App - Form"
    ).collect(Collectors.toSet());

    @Override
    public void importedPath(final PackageId packageId, final String path, final Node node, final PathAction action)
            throws RepositoryException {
        if (path.startsWith("/apps/classic-app/components/")
                && node.isNodeType("{http://www.day.com/jcr/cq/1.0}Component")) {
            if (!node.hasProperty("componentGroup")) {
                reporting(builder -> builder
                        .withPackage(packageId)
                        .withDescription(path + ": component missing componentGroup property"));
            } else {
                final String componentGroup = node.getProperty("componentGroup").getString();
                if (path.startsWith("/apps/classic-app/components/form/") && !formGroups.contains(componentGroup)) {
                    reporting(builder -> builder
                            .withPackage(packageId)
                            .withDescription(path + String.format(": invalid group '%s' for form component (%s)",
                                    componentGroup, formGroups))
                    );
                } else if (!validGroups.contains(componentGroup)) {
                    reporting(builder -> builder
                            .withPackage(packageId)
                            .withDescription(path + String.format(": invalid group '%s' for classic-app component (%s)",
                                    componentGroup, validGroups))
                    );
                }
            }
        }
    }
}
