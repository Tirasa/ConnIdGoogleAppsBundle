/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 ConnId All Rights Reserved
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
 */
package net.tirasa.connid.bundles.googleapps;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class GoogleAppsCustomSchema {

    @JsonProperty
    private String name;

    @JsonProperty
    private Boolean multiValued;

    @JsonProperty
    private String type;

    @JsonProperty
    private List<GoogleAppsCustomSchema> innerSchemas = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Boolean getMultiValued() {
        return multiValued;
    }

    public void setMultiValued(final Boolean multiValued) {
        this.multiValued = multiValued;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public List<GoogleAppsCustomSchema> getInnerSchemas() {
        return innerSchemas;
    }

    public void setInnerSchemas(final List<GoogleAppsCustomSchema> innerSchemas) {
        this.innerSchemas.clear();
        this.innerSchemas.addAll(innerSchemas);
    }
}
