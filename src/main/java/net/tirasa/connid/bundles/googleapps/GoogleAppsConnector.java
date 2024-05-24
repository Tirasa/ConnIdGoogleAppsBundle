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

import java.util.Optional;
import java.util.Set;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptionInfo;
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateDeltaOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

/**
 * Main implementation of the GoogleApps Connector.
 */
@ConnectorClass(displayNameKey = "GoogleApps.connector.display", configurationClass = GoogleAppsConfiguration.class)
public class GoogleAppsConnector
        implements PoolableConnector,
        TestOp, SchemaOp, SearchOp<Filter>,
        CreateOp, UpdateOp, UpdateDeltaOp, DeleteOp {

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link GoogleAppsConnector#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    private GoogleAppsConfiguration configuration;

    private Schema schema = null;

    /**
     * Gets the Configuration context for this connector.
     *
     * @return The current {@link Configuration}
     */
    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param configuration
     * the new {@link Configuration}
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    @Override
    public void init(final Configuration configuration) {
        this.configuration = (GoogleAppsConfiguration) configuration;
    }

    /**
     * Disposes of the {@link GoogleAppsConnector}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    @Override
    public void dispose() {
        Optional.ofNullable(configuration).ifPresent(GoogleAppsConfiguration::release);
        configuration = null;
    }

    @Override
    public void test() {
        try {
            configuration.test();
        } catch (Exception e) {
            throw new ConnectorException(e);
        }
    }

    @Override
    public void checkAlive() {
        test();
    }

    /** ****************
     * SPI Operations
     *
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     ***************** */
    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Schema schema() {
        if (null == schema) {
            final SchemaBuilder builder = new SchemaBuilder(GoogleAppsConnector.class);

            ObjectClassInfo user = UserHandler.getObjectClassInfo(configuration.getCustomSchemasJSON());
            builder.defineObjectClass(user);

            ObjectClassInfo group = GroupHandler.getObjectClassInfo();
            builder.defineObjectClass(group);

            ObjectClassInfo member = MembersHandler.getObjectClassInfo();
            builder.defineObjectClass(member);

            ObjectClassInfo orgUnit = OrgunitsHandler.getObjectClassInfo();
            builder.defineObjectClass(orgUnit);

            ObjectClassInfo licenseAssignment = LicenseAssignmentsHandler.getObjectClassInfo();
            builder.defineObjectClass(licenseAssignment);

            builder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsCookie(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildSortKeys(),
                    SearchOp.class);
            builder.defineOperationOption(
                    new OperationOptionInfo(GoogleAppsUtil.SHOW_DELETED_PARAM, Boolean.class), SearchOp.class);

            schema = builder.build();
        }
        return schema;
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(
            final ObjectClass objectClass,
            final OperationOptions options) {

        return CollectionUtil::newList;
    }

    @Override
    public void executeQuery(
            final ObjectClass objectClass,
            final Filter query,
            final ResultsHandler handler,
            final OperationOptions options) {

        new GoogleAppsSearch(configuration, objectClass, query, handler, options).execute();
    }

    @Override
    public Uid create(
            final ObjectClass objectClass,
            final Set<Attribute> createAttributes,
            final OperationOptions options) {

        return new GoogleAppsCreate(configuration, objectClass, createAttributes).execute();
    }

    @Override
    public Uid update(
            final ObjectClass objectClass,
            final Uid uid,
            final Set<Attribute> replaceAttributes,
            final OperationOptions options) {

        return new GoogleAppsUpdate(configuration, objectClass, uid).update(replaceAttributes);
    }

    @Override
    public Set<AttributeDelta> updateDelta(
            final ObjectClass objectClass,
            final Uid uid,
            final Set<AttributeDelta> modifications,
            final OperationOptions options) {

        return new GoogleAppsUpdate(configuration, objectClass, uid).updateDelta(modifications);
    }

    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        new GoogleAppsDelete(configuration, objectClass, uid).execute();
    }
}
