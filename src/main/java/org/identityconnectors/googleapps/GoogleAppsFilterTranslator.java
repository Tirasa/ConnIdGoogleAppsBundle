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

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.filter.AbstractFilterTranslator;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * The google apps api only supports fetching by id (equals)
 * 
 * @author Warren Strange
 * @version $Revision 1.0$
 * @since 1.0
 */
public class GoogleAppsFilterTranslator extends AbstractFilterTranslator<String> {

    @Override
    protected String createEqualsExpression(EqualsFilter filter, boolean not) {
        if (not) { //no way (natively) to search for "NotEquals"
            return null;
        }
        Attribute attr = filter.getAttribute();
        if (!attr.is(Name.NAME) && !attr.is(Uid.NAME)) {
            return null;
        }
        String name = attr.getName();
        String value = AttributeUtil.getAsStringValue(attr);
        if (checkSearchValue(value) == null) {
            return null;
        } else {
            return value;
        }
    }
    
    private String checkSearchValue(String value) {
        if (StringUtil.isEmpty(value)) {
            return null;
        }
        if (value.contains("*") || value.contains("&") || value.contains("|")) {
            throw new IllegalArgumentException("Value of search attribute contains illegal character(s).");
        }
        return value;
    }
}
