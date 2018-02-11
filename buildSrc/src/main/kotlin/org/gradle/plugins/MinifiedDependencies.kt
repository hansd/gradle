package org.gradle.plugins

import org.gradle.MinifyTransform
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

open class MinifiedDependencies : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        /**
         * A map from artifact name to a set of class name prefixes that should be kept.
         * Artifacts matched by this map will be minified to only contain the specified
         * classes and the classes they depend on. The classes are not relocated, they all
         * remain in their original namespace. This reduces the final Gradle distribution
         * size and makes us more conscious of which parts of a library we really need.
         */
        val keepPatterns = mapOf<String, Set<String>>("fastutil" to
            setOf("it.unimi.dsi.fastutil.ints.IntOpenHashSet", "it.unimi.dsi.fastutil.ints.IntSets"))

        val minified = Attribute.of("minified", Boolean::class.java)
        val artifactType = Attribute.of("artifactType", String::class.java)

        allprojects {
            plugins.withId("java-base") {
                // I could not make the dependencies DSL syntax work here
                getDependencies().attributesSchema {
                    attribute(minified)
                }

                getDependencies().artifactTypes.getByName("jar") {
                    attributes.attribute(minified, false)
                }

                getDependencies().registerTransform {
                    /*
                     * TODO Why do I have to add artifactType=jar here? According to
                     * the declaration above, it's the only artifact type for which
                     * minified=false anyway. If I don't add this, the transform chain
                     * in binary-compatibility.gradle no longer works.
                     */
                    from.attribute(minified, false).attribute(artifactType, "jar")
                    to.attribute(minified, true).attribute(artifactType, "jar")
                    artifactTransform(MinifyTransform::class.java) {
                        params(keepPatterns)
                    }
                }
            }

            // In the Groovy script the order of afterEvaluate and configiruations.all was reversed.
            // think it should be this way around.
            afterEvaluate {
                /*
                 * Some of our projects still depend on matching the default
                 * configuration. As soon as any attribute is added, the default
                 * configuraiton is no longer a valid match. To work around this
                 * we only add the "minified" attribute in places where we already
                 * use other attributes.
                 */
                configurations.all {
                    if (!attributes.isEmpty) {
                        attributes.attribute(minified, true)
                    }
                }
            }
        }

    }
}
