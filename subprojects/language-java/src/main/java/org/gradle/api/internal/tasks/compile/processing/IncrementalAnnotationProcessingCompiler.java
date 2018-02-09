/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Sets up incremental annotation processing before delegating to the actual Java compiler.
 */
public class IncrementalAnnotationProcessingCompiler implements Compiler<JavaCompileSpec> {

    private final Compiler<JavaCompileSpec> delegate;
    private final AnnotationProcessorDetector annotationProcessorDetector;

    public IncrementalAnnotationProcessingCompiler(Compiler<JavaCompileSpec> delegate, AnnotationProcessorDetector annotationProcessorDetector) {
        this.delegate = delegate;
        this.annotationProcessorDetector = annotationProcessorDetector;
    }

    @Override
    public WorkResult execute(JavaCompileSpec spec) {
        List<AnnotationProcessorDeclaration> annotationProcessors = getEffectiveAnnotationProcessors(spec);
        spec.setEffectiveAnnotationProcessors(annotationProcessors);
        return delegate.execute(spec);
    }

    /**
     * Scans the processor path for processor declarations. Filters them if the explicit <code>-processor</code> argument is given.
     * Treats explicit processors that didn't have a matching declaration on the path as non-incremental.
     */
    private List<AnnotationProcessorDeclaration> getEffectiveAnnotationProcessors(JavaCompileSpec spec) {
        List<AnnotationProcessorDeclaration> effectiveProcessors = Lists.newArrayList(annotationProcessorDetector.detectProcessors(spec.getAnnotationProcessorPath()));
        List<String> compilerArgs = spec.getCompileOptions().getCompilerArgs();
        int processorIndex = compilerArgs.lastIndexOf("-processor");
        if (processorIndex != -1) {
            Set<String> explicitProcessors = Sets.newHashSet(Splitter.on(',').split(compilerArgs.get(processorIndex + 1)));
            ListIterator<AnnotationProcessorDeclaration> iterator = effectiveProcessors.listIterator();
            while (iterator.hasNext()) {
                if (!explicitProcessors.remove(iterator.next().getClassName())) {
                    iterator.remove();
                }
            }
            for (String explicitProcessor : explicitProcessors) {
                effectiveProcessors.add(new AnnotationProcessorDeclaration(explicitProcessor, IncrementalAnnotationProcessorType.UNKNOWN));
            }
        }
        return effectiveProcessors;
    }
}
