/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.pce;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.openkilda.pce.PathComputerFactory.Strategy;
import org.openkilda.pce.impl.InMemoryPathComputer;
import org.openkilda.persistence.repositories.RepositoryFactory;

import org.junit.Test;

public class PathComputerFactoryTest {
    @Test
    public void shouldCreateAnInstance() {
        PathComputerFactory factory = new PathComputerFactory(
                mock(PathComputerConfig.class), mock(RepositoryFactory.class));
        PathComputer pathComputer = factory.getPathComputer(Strategy.COST);
        assertTrue(pathComputer instanceof InMemoryPathComputer);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldNotCreateNonCostPathComputer() {
        PathComputerFactory factory = new PathComputerFactory(
                mock(PathComputerConfig.class), mock(RepositoryFactory.class));
        factory.getPathComputer(Strategy.LATENCY);
    }
}
