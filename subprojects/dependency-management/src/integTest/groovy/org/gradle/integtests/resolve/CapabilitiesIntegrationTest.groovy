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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.jcenterRepositoryDefinition

class CapabilitiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            configurations { conf }
            repositories {
                ${jcenterRepositoryDefinition()}
            }           
        """
    }

    def "can choose between cglib and cglib-nodep by declaring capabilities"() {
        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               constraints {
                  capabilities {
                     capability('cglib') {
                        it.providedBy 'cglib:cglib'
                        it.providedBy 'cglib:cglib-nodep'
                        it.prefer 'cglib:cglib'
                     }
                  }
               }
            }
        """

        when:
        run 'dependencyInsight', '--configuration', 'conf', '--dependency', 'cglib'

        then:
        outputContains """cglib:cglib:3.2.5 (capability cglib is provided by cglib:cglib-nodep and cglib:cglib)
   variant "default"
\\--- conf

cglib:cglib-nodep:3.2.5 -> cglib:cglib:3.2.5
   variant "default"
\\--- conf
"""
    }

    def "fails resolution if 2 libraries provide the same capability"() {
        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               constraints {
                  capabilities {
                     capability('cglib') {
                        it.providedBy 'cglib:cglib'
                        it.providedBy 'cglib:cglib-nodep'
                     }
                  }
               }
            }
        """

        when:
        fails 'dependencyInsight', '--configuration', 'conf', '--dependency', 'cglib'

        then:
        failure.assertHasCause("Cannot choose between cglib:cglib-nodep or cglib:cglib because they provide the same capability: cglib")
    }

}
