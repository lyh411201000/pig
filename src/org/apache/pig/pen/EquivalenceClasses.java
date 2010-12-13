/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pig.pen;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.LOForEach;
import org.apache.pig.impl.logicalLayer.LOCross;
import org.apache.pig.impl.logicalLayer.LogicalOperator;
import org.apache.pig.impl.logicalLayer.LogicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.impl.util.IdentityHashSet;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.*;
import org.apache.pig.impl.plan.VisitorException;


//These methods are used to generate equivalence classes given the operator name and the output from the operator
//For example, it gives out 2 eq. classes for filter, one that passes the filter and one that doesn't
public class EquivalenceClasses {
    
    public static Map<LogicalOperator, Collection<IdentityHashSet<Tuple>>> getLoToEqClassMap(PhysicalPlan plan,
        LogicalPlan lp, Map<LogicalOperator, PhysicalOperator> logToPhyMap,
        Map<LogicalOperator, DataBag> logToDataMap,
        Map<LOForEach, Map<LogicalOperator, PhysicalOperator>> forEachInnerLogToPhyMap,
        final HashMap<PhysicalOperator, Collection<IdentityHashSet<Tuple>>> poToEqclassesMap)
        throws VisitorException {
        Map<LogicalOperator, Collection<IdentityHashSet<Tuple>>> ret =
          new HashMap<LogicalOperator, Collection<IdentityHashSet<Tuple>>>();
        List<LogicalOperator> roots = lp.getRoots();
        HashSet<LogicalOperator> seen = new HashSet<LogicalOperator>();
        for(LogicalOperator lo: roots) {
            getEqClasses(plan, lo, lp, logToPhyMap, ret, poToEqclassesMap, logToDataMap, forEachInnerLogToPhyMap, seen);
        }
        return ret;
    }
    
    private static void getEqClasses(PhysicalPlan plan, LogicalOperator parent, LogicalPlan lp,
        Map<LogicalOperator, PhysicalOperator> logToPhyMap, Map<LogicalOperator,
        Collection<IdentityHashSet<Tuple>>> result,
        final HashMap<PhysicalOperator, Collection<IdentityHashSet<Tuple>>> poToEqclassesMap,
        Map<LogicalOperator, DataBag> logToDataMap,
        Map<LOForEach, Map<LogicalOperator, PhysicalOperator>> forEachInnerLogToPhyMap,
        HashSet<LogicalOperator> seen) throws VisitorException {
        if (parent instanceof LOForEach) {
            if (poToEqclassesMap.get(logToPhyMap.get(parent)) != null) {
                LinkedList<IdentityHashSet<Tuple>> eqClasses = new LinkedList<IdentityHashSet<Tuple>>();
                eqClasses.addAll(poToEqclassesMap.get(logToPhyMap.get(parent)));
                for (Map.Entry<LogicalOperator, PhysicalOperator> entry : forEachInnerLogToPhyMap.get(parent).entrySet()) {
                    if (poToEqclassesMap.get(entry.getValue()) != null)
                        eqClasses.addAll(poToEqclassesMap.get(entry.getValue()));
                }
                result.put(parent, eqClasses);
            }
        } else if (parent instanceof LOCross) {
            boolean ok = true; 
            for (LogicalOperator input : ((LOCross) parent).getInputs()) {
                if (logToDataMap.get(input).size() < 2) {
                    // only if all inputs have at least more than two tuples will all outputs be added to the eq. class
                    ok = false;
                    break;
                }
            }
            if (ok) {
                LinkedList<IdentityHashSet<Tuple>> eqClasses = new LinkedList<IdentityHashSet<Tuple>>();
                IdentityHashSet<Tuple> eqClass = new IdentityHashSet<Tuple>();
                for (Iterator<Tuple> it = logToDataMap.get(parent).iterator(); it.hasNext();) {
                    eqClass.add(it.next());
                }
                eqClasses.add(eqClass);
                result.put(parent, eqClasses);
            } else {
                LinkedList<IdentityHashSet<Tuple>> eqClasses = new LinkedList<IdentityHashSet<Tuple>>();
                IdentityHashSet<Tuple> eqClass = new IdentityHashSet<Tuple>();
                eqClasses.add(eqClass);
                result.put(parent, eqClasses);
            }
        } else {
            Collection<IdentityHashSet<Tuple>> eqClasses = poToEqclassesMap.get(logToPhyMap.get(parent));
            if (eqClasses == null) {
                eqClasses = new LinkedList<IdentityHashSet<Tuple>>();
                int size = ((POPackage)logToPhyMap.get(parent)).getNumInps();
                for (int i = 0; i < size; i++) {
                    eqClasses.add(new IdentityHashSet<Tuple>());
                }
            }
            result.put(parent, eqClasses);
        }
        // result.put(parent, getEquivalenceClasses(plan, parent, lp, logToPhyMap, poToEqclassesMap));
        if (lp.getSuccessors(parent) != null) {
            for (LogicalOperator lo : lp.getSuccessors(parent)) {
                if (!seen.contains(lo)) {
                    seen.add(lo);
                    getEqClasses(plan, lo, lp, logToPhyMap, result, poToEqclassesMap, logToDataMap, forEachInnerLogToPhyMap, seen);
                }
            }
        }
    }
}
