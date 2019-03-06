/**
 * Copyright © 2018 ConnId (connid-dev@googlegroups.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
