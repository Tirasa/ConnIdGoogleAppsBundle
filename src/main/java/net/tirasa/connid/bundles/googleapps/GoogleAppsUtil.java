/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2016 ConnId.
 */
package net.tirasa.connid.bundles.googleapps;

import org.identityconnectors.common.StringUtil;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;

public class GoogleAppsUtil {

    public static String getName(Name name) {
        if (name != null) {
            String email = name.getNameValue();
            if (StringUtil.isBlank(email)) {
                throw new InvalidAttributeValueException("Required attribute __NAME__ is blank");
            }
            return email;
        }
        throw new InvalidAttributeValueException("Required attribute __NAME__ is missing");
    }

    public static String getStringValueWithDefault(Attribute source, String defaultTo) {
        Object value = AttributeUtil.getSingleValue(source);
        if (value instanceof String) {
            return (String) value;
        } else if (null != value) {
            throw new InvalidAttributeValueException("The " + source.getName()
                    + " attribute is not String value attribute. It has value with type "
                    + value.getClass().getSimpleName());
        }
        return defaultTo;
    }

    public static Boolean getBooleanValueWithDefault(Attribute source, Boolean defaultTo) {
        Object value = AttributeUtil.getSingleValue(source);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (null != value) {
            throw new InvalidAttributeValueException("The " + source.getName()
                    + " attribute is not Boolean value attribute. It has value with type "
                    + value.getClass().getSimpleName());
        }
        return defaultTo;
    }
}
