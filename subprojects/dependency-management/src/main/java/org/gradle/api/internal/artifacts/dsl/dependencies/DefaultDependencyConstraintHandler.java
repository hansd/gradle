/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.dsl.dependencies;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentModuleMetadata;
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.dsl.CapabilitiesHandler;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class DefaultDependencyConstraintHandler implements DependencyConstraintHandler, MethodMixIn {
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactory dependencyFactory;
    private final DynamicAddDependencyMethods dynamicMethods;
    private final CapabilitiesHandler capabilitiesHandler;

    public DefaultDependencyConstraintHandler(ConfigurationContainer configurationContainer,
                                              DependencyFactory dependencyFactory,
                                              CapabilitiesHandler capabilitiesHandler) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.dynamicMethods = new DynamicAddDependencyMethods(configurationContainer, new DependencyConstraintAdder());
        this.capabilitiesHandler = capabilitiesHandler;
    }

    @Override
    public DependencyConstraint add(String configurationName, Object dependencyNotation) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, null);
    }

    @Override
    public DependencyConstraint add(String configurationName, Object dependencyNotation, Action<? super DependencyConstraint> configureAction) {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureAction);
    }

    @Override
    public DependencyConstraint create(Object dependencyNotation) {
        return doCreate(dependencyNotation, null);
    }

    @Override
    public DependencyConstraint create(Object dependencyNotation, Action<? super DependencyConstraint> configureAction) {
        return doCreate(dependencyNotation, configureAction);
    }

    @Override
    public void capabilities(Action<? super CapabilitiesHandler> configureAction) {
        configureAction.execute(capabilitiesHandler);
    }

    private DependencyConstraint doCreate(Object dependencyNotation, @Nullable Action<? super DependencyConstraint> configureAction) {
        DependencyConstraint dependencyConstraint = dependencyFactory.createDependencyConstraint(dependencyNotation);
        if (configureAction != null) {
            configureAction.execute(dependencyConstraint);
        }
        return dependencyConstraint;
    }

    private DependencyConstraint doAdd(Configuration configuration, Object dependencyNotation, @Nullable Action<? super DependencyConstraint> configureAction) {
        DependencyConstraint dependency = doCreate(dependencyNotation, configureAction);
        configuration.getDependencies().add(dependency);
        return dependency;
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return dynamicMethods;
    }

    private class DependencyConstraintAdder implements DynamicAddDependencyMethods.DependencyAdder {
        @Override
        public Dependency add(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
            DependencyConstraint dependencyConstraint = ConfigureUtil.configure(configureClosure, dependencyFactory.createDependencyConstraint(dependencyNotation));
            configuration.getDependencies().add(dependencyConstraint);
            return dependencyConstraint;
        }
    }

    public final static class CapabilitiesHandlerSpike implements CapabilitiesHandler {
        private final ComponentModuleMetadataHandler metadataHandler;
        private final Map<String, CapabilitySpike> capabilities = Maps.newHashMap();
        private final Multimap<String, String> moduleToCapability = ArrayListMultimap.create();

        public CapabilitiesHandlerSpike(ComponentModuleMetadataHandler metadataHandler) {
            this.metadataHandler = metadataHandler;
        }

        @Override
        public void capability(String identifier, Action<? super CapabilityHandler> configureAction) {
            CapabilitySpike capability = capabilities.get(identifier);
            if (capability == null) {
                capability = new CapabilitySpike(identifier);
                capabilities.put(identifier, capability);
            }
            configureAction.execute(capability);
        }

        public Map<String, Set<String>> conflictingModules(String module) {
            Collection<String> ids = moduleToCapability.get(module);
            Map<String, Set<String>> result = Maps.newHashMap();
            for (String capability : ids) {
                CapabilitySpike capabilitySpike = capabilities.get(capability);
                if (capabilitySpike.prefer == null) {
                    // there's no solution registered for this capability
                    result.put(capability, capabilitySpike.providedBy);
                }
            }
            return result;
        }

        public void convertToReplacementRules() {
            for (Map.Entry<String, CapabilitySpike> capabilityEntry : capabilities.entrySet()) {
                CapabilitySpike capabilityValue = capabilityEntry.getValue();
                final String prefer = capabilityValue.prefer;
                if (prefer != null) {
                    final String because = "capability " + capabilityEntry.getKey() + " is provided by " + Joiner.on(" and ").join(capabilityValue.providedBy);
                    for (String module : capabilityValue.providedBy) {
                        if (!module.equals(prefer)) {
                            metadataHandler.module(module, new Action<ComponentModuleMetadata>() {
                                @Override
                                public void execute(ComponentModuleMetadata componentModuleMetadata) {
                                    ((ComponentModuleMetadataDetails) componentModuleMetadata).replacedBy(prefer, because);
                                }
                            });
                        }
                    }
                }
            }
        }

        private final class CapabilitySpike implements CapabilityHandler {
            private final String id;
            private final Set<String> providedBy = Sets.newHashSet();
            private String prefer;

            private CapabilitySpike(String id) {
                this.id = id;
            }

            @Override
            public void providedBy(String moduleIdentifier) {
                moduleToCapability.put(moduleIdentifier, id);
                providedBy.add(moduleIdentifier);
            }

            @Override
            public void prefer(String moduleIdentifer) {
                prefer = moduleIdentifer;
            }
        }

    }

}
