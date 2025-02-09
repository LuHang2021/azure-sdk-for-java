// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.resourcemanager.resources;

import com.azure.core.http.HttpPipeline;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.resourcemanager.test.utils.TestUtilities;
import com.azure.core.management.Region;
import com.azure.resourcemanager.resources.fluentcore.model.Accepted;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentOperation;
import com.azure.resourcemanager.resources.models.GenericResource;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.ResourceGroupExportTemplateOptions;
import com.azure.resourcemanager.resources.models.ResourceGroups;
import com.azure.resourcemanager.resources.models.WhatIfOperationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeploymentsTests extends ResourceManagementTest {
    private ResourceGroups resourceGroups;
    private ResourceGroup resourceGroup;

    private String testId;
    private String rgName;
    private static final String TEMPLATE_URI = "https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/quickstarts/microsoft.network/vnet-two-subnets/azuredeploy.json";
    private static final String BLANK_TEMPLATE_URI = "https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/100-blank-template/azuredeploy.json";
    private static final String PARAMETERS_URI = "https://raw.githubusercontent.com/Azure/azure-quickstart-templates/master/quickstarts/microsoft.network/vnet-two-subnets/azuredeploy.parameters.json";
    private static final String UPDATE_TEMPLATE = "{\"$schema\":\"https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#\",\"contentVersion\":\"1.0.0.0\",\"parameters\":{\"vnetName\":{\"type\":\"string\",\"defaultValue\":\"VNet2\",\"metadata\":{\"description\":\"VNet name\"}},\"vnetAddressPrefix\":{\"type\":\"string\",\"defaultValue\":\"10.0.0.0/16\",\"metadata\":{\"description\":\"Address prefix\"}},\"subnet1Prefix\":{\"type\":\"string\",\"defaultValue\":\"10.0.0.0/24\",\"metadata\":{\"description\":\"Subnet 1 Prefix\"}},\"subnet1Name\":{\"type\":\"string\",\"defaultValue\":\"Subnet1\",\"metadata\":{\"description\":\"Subnet 1 Name\"}},\"subnet2Prefix\":{\"type\":\"string\",\"defaultValue\":\"10.0.1.0/24\",\"metadata\":{\"description\":\"Subnet 2 Prefix\"}},\"subnet2Name\":{\"type\":\"string\",\"defaultValue\":\"Subnet222\",\"metadata\":{\"description\":\"Subnet 2 Name\"}}},\"variables\":{\"apiVersion\":\"2015-06-15\"},\"resources\":[{\"apiVersion\":\"[variables('apiVersion')]\",\"type\":\"Microsoft.Network/virtualNetworks\",\"name\":\"[parameters('vnetName')]\",\"location\":\"[resourceGroup().location]\",\"properties\":{\"addressSpace\":{\"addressPrefixes\":[\"[parameters('vnetAddressPrefix')]\"]},\"subnets\":[{\"name\":\"[parameters('subnet1Name')]\",\"properties\":{\"addressPrefix\":\"[parameters('subnet1Prefix')]\"}},{\"name\":\"[parameters('subnet2Name')]\",\"properties\":{\"addressPrefix\":\"[parameters('subnet2Prefix')]\"}}]}}]}";
    private static final String UPDATE_PARAMETERS = "{\"vnetAddressPrefix\":{\"value\":\"10.0.0.0/16\"},\"subnet1Name\":{\"value\":\"Subnet1\"},\"subnet1Prefix\":{\"value\":\"10.0.0.0/24\"}}";
    private static final String CONTENT_VERSION = "1.0.0.0";

    @Override
    protected void initializeClients(HttpPipeline httpPipeline, AzureProfile profile) {
        super.initializeClients(httpPipeline, profile);
        testId = generateRandomResourceName("", 9);
        resourceGroups = resourceClient.resourceGroups();
        rgName = "rg" + testId;
        resourceGroup = resourceGroups.define(rgName)
            .withRegion(Region.US_SOUTH_CENTRAL)
            .create();
    }

    @Override
    protected void cleanUpResources() {
        resourceGroups.beginDeleteByName(rgName);
    }

    @Test
    public void canDeployVirtualNetwork() throws Exception {
        final String dpName = "dpA" + testId;

        // Create
        resourceClient.deployments()
            .define(dpName)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .create();
        // List
        PagedIterable<Deployment> deployments = resourceClient.deployments().listByResourceGroup(rgName);
        boolean found = false;
        for (Deployment deployment : deployments) {
            if (deployment.name().equals(dpName)) {
                found = true;
            }
        }
        Assertions.assertTrue(found);
        // Check existence
        Assertions.assertTrue(resourceClient.deployments().checkExistence(rgName, dpName));

        // Get
        Deployment deployment = resourceClient.deployments().getByResourceGroup(rgName, dpName);
        Assertions.assertNotNull(deployment);
        Assertions.assertEquals("Succeeded", deployment.provisioningState());
        GenericResource generic = resourceClient.genericResources().get(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet1", "2015-06-15");
        Assertions.assertNotNull(generic);
        // Export
        Assertions.assertNotNull(deployment.exportTemplate().templateAsJson());
        // Export from resource group
        Assertions.assertNotNull(resourceGroup.exportTemplate(ResourceGroupExportTemplateOptions.INCLUDE_BOTH));
        // Deployment operations
        PagedIterable<DeploymentOperation> operations = deployment.deploymentOperations().list();
        Assertions.assertEquals(5, TestUtilities.getSize(operations));
        DeploymentOperation op = deployment.deploymentOperations().getById(operations.iterator().next().operationId());
        Assertions.assertNotNull(op);
        resourceClient.genericResources().delete(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet1", "2015-06-15");
    }

    @Test
    public void canPostDeploymentWhatIfOnResourceGroup() throws Exception {
        final String dpName = "dpA" + testId;

        // Create
        resourceClient.deployments()
            .define(dpName)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .create();
        // List
        PagedIterable<Deployment> deployments = resourceClient.deployments().listByResourceGroup(rgName);
        boolean found = false;
        for (Deployment deployment : deployments) {
            if (deployment.name().equals(dpName)) {
                found = true;
            }
        }
        Assertions.assertTrue(found);

        // Get
        Deployment deployment = resourceClient.deployments().getByResourceGroup(rgName, dpName);
        Assertions.assertNotNull(deployment);
        Assertions.assertEquals("Succeeded", deployment.provisioningState());

        //What if
        WhatIfOperationResult result = deployment.prepareWhatIf()
            .withIncrementalMode()
            .withWhatIfTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .whatIf();

        Assertions.assertEquals("Succeeded", result.status());
        Assertions.assertEquals(3, result.changes().size());

        resourceClient.genericResources().delete(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet1", "2015-06-15");
    }

    @Test
    public void canPostDeploymentWhatIfOnSubscription() throws Exception {
        final String dpName = "dpA" + testId;

        // Create
        resourceClient.deployments()
            .define(dpName)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .create();
        // List
        PagedIterable<Deployment> deployments = resourceClient.deployments().listByResourceGroup(rgName);
        boolean found = false;
        for (Deployment deployment : deployments) {
            if (deployment.name().equals(dpName)) {
                found = true;
            }
        }
        Assertions.assertTrue(found);

        // Get
        Deployment deployment = resourceClient.deployments().getByResourceGroup(rgName, dpName);
        Assertions.assertNotNull(deployment);
        Assertions.assertEquals("Succeeded", deployment.provisioningState());

        //What if
        WhatIfOperationResult result = deployment.prepareWhatIf()
            .withLocation("westus")
            .withIncrementalMode()
            .withWhatIfTemplateLink(BLANK_TEMPLATE_URI, CONTENT_VERSION)
            .whatIfAtSubscriptionScope();

        Assertions.assertEquals("Succeeded", result.status());
        Assertions.assertEquals(0, result.changes().size());

        resourceClient.genericResources().delete(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet1", "2015-06-15");
    }

    @Test
    @Disabled("deployment.cancel() doesn't throw but provisining state says Running not Cancelled...")
    public void canCancelVirtualNetworkDeployment() throws Exception {
        final String dp = "dpB" + testId;

        // Begin create
        resourceClient.deployments()
            .define(dp)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .beginCreate();
        Deployment deployment = resourceClient.deployments().getByResourceGroup(rgName, dp);
        Assertions.assertEquals(dp, deployment.name());
        // Cancel
        deployment.cancel();
        deployment = resourceClient.deployments().getByResourceGroup(rgName, dp);
        Assertions.assertEquals("Canceled", deployment.provisioningState());
        Assertions.assertFalse(resourceClient.genericResources().checkExistence(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet1", "2015-06-15"));
    }

    @Test
    public void canUpdateVirtualNetworkDeployment() throws Exception {
        final String dp = "dpC" + testId;

        // Begin create
        Accepted<Deployment> acceptedDeployment = resourceClient.deployments()
            .define(dp)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .beginCreate();
        Deployment createdDeployment = acceptedDeployment.getActivationResponse().getValue();
        Deployment deployment = resourceClient.deployments().getByResourceGroup(rgName, dp);
        Assertions.assertEquals(createdDeployment.correlationId(), deployment.correlationId());
        Assertions.assertEquals(dp, deployment.name());
        // Cancel
        deployment.cancel();
        deployment = resourceClient.deployments().getByResourceGroup(rgName, dp);
        Assertions.assertEquals("Canceled", deployment.provisioningState());
        // Update
        deployment.update()
            .withTemplate(UPDATE_TEMPLATE)
            .withParameters(UPDATE_PARAMETERS)
            .withMode(DeploymentMode.INCREMENTAL)
            .apply();
        deployment = resourceClient.deployments().getByResourceGroup(rgName, dp);
        Assertions.assertEquals(DeploymentMode.INCREMENTAL, deployment.mode());
        Assertions.assertEquals("Succeeded", deployment.provisioningState());
        GenericResource genericVnet = resourceClient.genericResources().get(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet2", "2015-06-15");
        Assertions.assertNotNull(genericVnet);
        resourceClient.genericResources().delete(rgName, "Microsoft.Network", "", "virtualnetworks", "VNet2", "2015-06-15");
    }

    @Test
    public void canDeployVirtualNetworkSyncPoll() throws Exception {
        final long defaultDelayInMillis = 10 * 1000;

        final String dp = "dpD" + testId;

        // Begin create
        Accepted<Deployment> acceptedDeployment = resourceClient.deployments()
            .define(dp)
            .withExistingResourceGroup(rgName)
            .withTemplateLink(TEMPLATE_URI, CONTENT_VERSION)
            .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
            .withMode(DeploymentMode.COMPLETE)
            .beginCreate();
        Deployment createdDeployment = acceptedDeployment.getActivationResponse().getValue();
        Assertions.assertNotEquals("Succeeded", createdDeployment.provisioningState());

        LongRunningOperationStatus pollStatus = acceptedDeployment.getActivationResponse().getStatus();
        long delayInMills = acceptedDeployment.getActivationResponse().getRetryAfter() == null
            ? defaultDelayInMillis
            : acceptedDeployment.getActivationResponse().getRetryAfter().toMillis();
        while (!pollStatus.isComplete()) {
            ResourceManagerUtils.sleep(Duration.ofMillis(delayInMills));

            PollResponse<?> pollResponse = acceptedDeployment.getSyncPoller().poll();
            pollStatus = pollResponse.getStatus();
            delayInMills = pollResponse.getRetryAfter() == null
                ? defaultDelayInMillis
                : pollResponse.getRetryAfter().toMillis();
        }
        Assertions.assertEquals(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED, pollStatus);
        Deployment deployment = acceptedDeployment.getFinalResult();
        Assertions.assertEquals("Succeeded", deployment.provisioningState());
    }

    @Test
    public void canDeployVirtualNetworkSyncPollWithFailure() throws Exception {
        final long defaultDelayInMillis = 10 * 1000;

        final String templateJson = "{ \"$schema\": \"https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#\", \"contentVersion\": \"1.0.0.0\", \"resources\": [ { \"type\": \"Microsoft.Storage/storageAccounts\", \"apiVersion\": \"2019-04-01\", \"name\": \"satestnameconflict\", \"location\": \"eastus\", \"sku\": { \"name\": \"Standard_LRS\" }, \"kind\": \"StorageV2\", \"properties\": { \"supportsHttpsTrafficOnly\": true } } ] }";

        final String dp = "dpE" + testId;
        // Begin create
        Accepted<Deployment> acceptedDeployment = resourceClient.deployments()
            .define(dp)
            .withExistingResourceGroup(rgName)
            .withTemplate(templateJson)
            .withParameters("{}")
            .withMode(DeploymentMode.COMPLETE)
            .beginCreate();
        Deployment createdDeployment = acceptedDeployment.getActivationResponse().getValue();
        Assertions.assertNotEquals("Succeeded", createdDeployment.provisioningState());

        LongRunningOperationStatus pollStatus = acceptedDeployment.getActivationResponse().getStatus();
        long delayInMills = acceptedDeployment.getActivationResponse().getRetryAfter() == null
            ? defaultDelayInMillis
            : acceptedDeployment.getActivationResponse().getRetryAfter().toMillis();
        while (!pollStatus.isComplete()) {
            ResourceManagerUtils.sleep(Duration.ofMillis(delayInMills));

            PollResponse<?> pollResponse = acceptedDeployment.getSyncPoller().poll();
            pollStatus = pollResponse.getStatus();
            delayInMills = pollResponse.getRetryAfter() == null
                ? defaultDelayInMillis
                : pollResponse.getRetryAfter().toMillis();
        }
        Assertions.assertEquals(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED, pollStatus);
        Deployment deployment = acceptedDeployment.getFinalResult();
        Assertions.assertEquals("Succeeded", deployment.provisioningState());

        final String newRgName = generateRandomResourceName("rg", 10);
        try {
            final String dp2 = "dpF" + testId;

            // storage name conflict
            acceptedDeployment = resourceClient.deployments()
                .define(dp2)
                .withNewResourceGroup(newRgName, Region.US_EAST2)
                .withTemplate(templateJson)
                .withParameters("{}")
                .withMode(DeploymentMode.COMPLETE)
                .beginCreate();
            createdDeployment = acceptedDeployment.getActivationResponse().getValue();
            Assertions.assertNotEquals("Succeeded", createdDeployment.provisioningState());

            pollStatus = acceptedDeployment.getActivationResponse().getStatus();
            delayInMills = acceptedDeployment.getActivationResponse().getRetryAfter() == null
                ? defaultDelayInMillis
                : (int) acceptedDeployment.getActivationResponse().getRetryAfter().toMillis();
            while (!pollStatus.isComplete()) {
                ResourceManagerUtils.sleep(Duration.ofMillis(delayInMills));

                PollResponse<?> pollResponse = acceptedDeployment.getSyncPoller().poll();
                pollStatus = pollResponse.getStatus();
                delayInMills = pollResponse.getRetryAfter() == null
                    ? defaultDelayInMillis
                    : (int) pollResponse.getRetryAfter().toMillis();
            }
            Assertions.assertEquals(LongRunningOperationStatus.FAILED, pollStatus);

            // check exception
            boolean exceptionOnFinalResult = false;
            try {
                deployment = acceptedDeployment.getFinalResult();
            } catch (ManagementException exception) {
                exceptionOnFinalResult = true;

                ManagementError managementError = exception.getValue();
                Assertions.assertEquals("DeploymentFailed", managementError.getCode());
                Assertions.assertNotNull(managementError.getMessage());
            }
            Assertions.assertTrue(exceptionOnFinalResult);

            // check operations
            deployment = resourceClient.deployments().getByResourceGroup(newRgName, dp2);
            Assertions.assertEquals("Failed", deployment.provisioningState());
            PagedIterable<DeploymentOperation> operations = deployment.deploymentOperations().list();
            Optional<DeploymentOperation> failedOperation = operations.stream()
                .filter(o -> "Failed".equalsIgnoreCase(o.provisioningState())).findFirst();
            Assertions.assertTrue(failedOperation.isPresent());
            Assertions.assertEquals("Conflict", failedOperation.get().statusCode());

            // check poll result again, should stay failed
            Assertions.assertEquals(LongRunningOperationStatus.FAILED, acceptedDeployment.getSyncPoller().poll().getStatus());
            exceptionOnFinalResult = false;
            try {
                deployment = acceptedDeployment.getFinalResult();
            } catch (ManagementException exception) {
                exceptionOnFinalResult = true;
            }
            Assertions.assertTrue(exceptionOnFinalResult);
        } finally {
            resourceClient.resourceGroups().beginDeleteByName(newRgName);
        }
    }

    @Test
    public void canGetErrorWhenDeploymentFail() throws Exception {
        final String dpName = "dpG" + testId;

        String templateJson;    // template fails at Subnet2
        try (InputStream templateStream = this.getClass().getResourceAsStream("/deployTemplateWithError.json")) {
            templateJson = new BufferedReader(new InputStreamReader(templateStream, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        }

        ManagementError deploymentError = null;
        try {
            resourceClient.deployments()
                .define(dpName)
                .withExistingResourceGroup(rgName)
                .withTemplate(templateJson)
                .withParametersLink(PARAMETERS_URI, CONTENT_VERSION)
                .withMode(DeploymentMode.COMPLETE)
                .create();
        } catch (ManagementException deploymentException) {
            // verify ManagementException
            Assertions.assertTrue(deploymentException.getValue().getDetails().stream()
                .anyMatch(detail -> detail.getMessage().contains("Subnet2")));

            Deployment failedDeployment = resourceClient.deployments()
                .getByResourceGroup(rgName, dpName);
            deploymentError = failedDeployment.error();

            // verify deployment operations
            PagedIterable<DeploymentOperation> operations = failedDeployment.deploymentOperations().list();
            Assertions.assertTrue(operations.stream()
                .anyMatch(operation -> "BadRequest".equals(operation.statusCode())
                    && operation.targetResource().resourceName().contains("Subnet2")));
        }
        // verify Deployment.error()
        Assertions.assertNotNull(deploymentError);
        Assertions.assertTrue(deploymentError.getDetails().stream()
            .anyMatch(detail -> detail.getMessage().contains("Subnet2")));
    }
}
