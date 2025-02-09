// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.resourcemanager.postgresqlflexibleserver.models;

import com.azure.core.annotation.Fluent;
import com.azure.core.annotation.JsonFlatten;
import com.azure.core.util.logging.ClientLogger;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Represents a server to be updated. */
@JsonFlatten
@Fluent
public class ServerForUpdate {
    @JsonIgnore private final ClientLogger logger = new ClientLogger(ServerForUpdate.class);

    /*
     * The location the resource resides in.
     */
    @JsonProperty(value = "location")
    private String location;

    /*
     * The SKU (pricing tier) of the server.
     */
    @JsonProperty(value = "sku")
    private Sku sku;

    /*
     * Application-specific metadata in the form of key-value pairs.
     */
    @JsonProperty(value = "tags")
    @JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.ALWAYS)
    private Map<String, String> tags;

    /*
     * The password of the administrator login.
     */
    @JsonProperty(value = "properties.administratorLoginPassword")
    private String administratorLoginPassword;

    /*
     * Storage properties of a server.
     */
    @JsonProperty(value = "properties.storage")
    private Storage storage;

    /*
     * Backup properties of a server.
     */
    @JsonProperty(value = "properties.backup")
    private Backup backup;

    /*
     * High availability properties of a server.
     */
    @JsonProperty(value = "properties.highAvailability")
    private HighAvailability highAvailability;

    /*
     * Maintenance window properties of a server.
     */
    @JsonProperty(value = "properties.maintenanceWindow")
    private MaintenanceWindow maintenanceWindow;

    /*
     * The mode to update a new PostgreSQL server.
     */
    @JsonProperty(value = "properties.createMode")
    private CreateModeForUpdate createMode;

    /**
     * Get the location property: The location the resource resides in.
     *
     * @return the location value.
     */
    public String location() {
        return this.location;
    }

    /**
     * Set the location property: The location the resource resides in.
     *
     * @param location the location value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withLocation(String location) {
        this.location = location;
        return this;
    }

    /**
     * Get the sku property: The SKU (pricing tier) of the server.
     *
     * @return the sku value.
     */
    public Sku sku() {
        return this.sku;
    }

    /**
     * Set the sku property: The SKU (pricing tier) of the server.
     *
     * @param sku the sku value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withSku(Sku sku) {
        this.sku = sku;
        return this;
    }

    /**
     * Get the tags property: Application-specific metadata in the form of key-value pairs.
     *
     * @return the tags value.
     */
    public Map<String, String> tags() {
        return this.tags;
    }

    /**
     * Set the tags property: Application-specific metadata in the form of key-value pairs.
     *
     * @param tags the tags value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withTags(Map<String, String> tags) {
        this.tags = tags;
        return this;
    }

    /**
     * Get the administratorLoginPassword property: The password of the administrator login.
     *
     * @return the administratorLoginPassword value.
     */
    public String administratorLoginPassword() {
        return this.administratorLoginPassword;
    }

    /**
     * Set the administratorLoginPassword property: The password of the administrator login.
     *
     * @param administratorLoginPassword the administratorLoginPassword value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withAdministratorLoginPassword(String administratorLoginPassword) {
        this.administratorLoginPassword = administratorLoginPassword;
        return this;
    }

    /**
     * Get the storage property: Storage properties of a server.
     *
     * @return the storage value.
     */
    public Storage storage() {
        return this.storage;
    }

    /**
     * Set the storage property: Storage properties of a server.
     *
     * @param storage the storage value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withStorage(Storage storage) {
        this.storage = storage;
        return this;
    }

    /**
     * Get the backup property: Backup properties of a server.
     *
     * @return the backup value.
     */
    public Backup backup() {
        return this.backup;
    }

    /**
     * Set the backup property: Backup properties of a server.
     *
     * @param backup the backup value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withBackup(Backup backup) {
        this.backup = backup;
        return this;
    }

    /**
     * Get the highAvailability property: High availability properties of a server.
     *
     * @return the highAvailability value.
     */
    public HighAvailability highAvailability() {
        return this.highAvailability;
    }

    /**
     * Set the highAvailability property: High availability properties of a server.
     *
     * @param highAvailability the highAvailability value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withHighAvailability(HighAvailability highAvailability) {
        this.highAvailability = highAvailability;
        return this;
    }

    /**
     * Get the maintenanceWindow property: Maintenance window properties of a server.
     *
     * @return the maintenanceWindow value.
     */
    public MaintenanceWindow maintenanceWindow() {
        return this.maintenanceWindow;
    }

    /**
     * Set the maintenanceWindow property: Maintenance window properties of a server.
     *
     * @param maintenanceWindow the maintenanceWindow value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withMaintenanceWindow(MaintenanceWindow maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
        return this;
    }

    /**
     * Get the createMode property: The mode to update a new PostgreSQL server.
     *
     * @return the createMode value.
     */
    public CreateModeForUpdate createMode() {
        return this.createMode;
    }

    /**
     * Set the createMode property: The mode to update a new PostgreSQL server.
     *
     * @param createMode the createMode value to set.
     * @return the ServerForUpdate object itself.
     */
    public ServerForUpdate withCreateMode(CreateModeForUpdate createMode) {
        this.createMode = createMode;
        return this;
    }

    /**
     * Validates the instance.
     *
     * @throws IllegalArgumentException thrown if the instance is not valid.
     */
    public void validate() {
        if (sku() != null) {
            sku().validate();
        }
        if (storage() != null) {
            storage().validate();
        }
        if (backup() != null) {
            backup().validate();
        }
        if (highAvailability() != null) {
            highAvailability().validate();
        }
        if (maintenanceWindow() != null) {
            maintenanceWindow().validate();
        }
    }
}
