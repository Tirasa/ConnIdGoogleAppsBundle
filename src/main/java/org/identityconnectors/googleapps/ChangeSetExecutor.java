/**
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 * Copyright 2011-2013 Tirasa. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License"). You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at https://oss.oracle.com/licenses/CDDL
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at https://oss.oracle.com/licenses/CDDL.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.googleapps;

import java.util.List;

/**
 * Utility class to apply the minimal set of changes to a list of Strings.
 *
 * Given the existing list (as it exists on the resource currently), and the new updated list
 * (as it should exist after the updates are compete) - this invokes the correct additions and removals.
 *
 *
 * To use this provide an anonymous inner class that inherits from this class and supplyies the abstract
 * doAdd() and doRemove() methods. 
 *
 * @author warrenstrange
 */
public abstract class ChangeSetExecutor {

    private ChangeSet changes;

    public ChangeSetExecutor(List<String> existingList, List<String> updatedList) {
        changes = new ChangeSet(existingList, updatedList);
    }

    /**
     * Add the item to the list of items on the target resource
     * @param item
     */
    public abstract void doAdd(String item);

    /**
     * remove the item from the list of items on the target resource
     * @param item
     */
    public abstract void doRemove(String item);

    /**
     * Apply all off the adds and removes on the target
     */
    public void execute() {
        // toAdd now contains all the net new names we need to add
        // add the new names
        for (String n : changes.itemsToBeAdded()) {
            doAdd(n);
        }

        for (String n : changes.itemsToBeRemoved()) {
            //log.info("Removing item {0}", n);
            doRemove(n);
        }
    }
}
