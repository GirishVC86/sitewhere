/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.event.persistence.cassandra;

import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Processor;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.sitewhere.cassandra.CassandraClient;
import com.sitewhere.event.persistence.DeviceEventManagementPersistence;
import com.sitewhere.event.persistence.streaming.DeviceAssignmentEventCreateProcessor;
import com.sitewhere.event.spi.microservice.IEventManagementMicroservice;
import com.sitewhere.rest.model.device.event.DeviceAlert;
import com.sitewhere.rest.model.device.event.DeviceLocation;
import com.sitewhere.rest.model.device.event.DeviceMeasurements;
import com.sitewhere.server.lifecycle.TenantEngineLifecycleComponent;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.SiteWhereSystemException;
import com.sitewhere.spi.device.IDeviceAssignment;
import com.sitewhere.spi.device.IDeviceManagement;
import com.sitewhere.spi.device.event.IDeviceAlert;
import com.sitewhere.spi.device.event.IDeviceCommandInvocation;
import com.sitewhere.spi.device.event.IDeviceCommandResponse;
import com.sitewhere.spi.device.event.IDeviceEvent;
import com.sitewhere.spi.device.event.IDeviceEventBatch;
import com.sitewhere.spi.device.event.IDeviceEventBatchResponse;
import com.sitewhere.spi.device.event.IDeviceEventManagement;
import com.sitewhere.spi.device.event.IDeviceLocation;
import com.sitewhere.spi.device.event.IDeviceMeasurements;
import com.sitewhere.spi.device.event.IDeviceStateChange;
import com.sitewhere.spi.device.event.IDeviceStreamData;
import com.sitewhere.spi.device.event.request.IDeviceAlertCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceAssignmentEventCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceCommandInvocationCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceCommandResponseCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceLocationCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceMeasurementsCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceStateChangeCreateRequest;
import com.sitewhere.spi.device.event.request.IDeviceStreamDataCreateRequest;
import com.sitewhere.spi.device.event.streaming.IEventStreamAck;
import com.sitewhere.spi.device.streaming.IDeviceStream;
import com.sitewhere.spi.error.ErrorCode;
import com.sitewhere.spi.error.ErrorLevel;
import com.sitewhere.spi.search.IDateRangeSearchCriteria;
import com.sitewhere.spi.search.ISearchResults;
import com.sitewhere.spi.server.lifecycle.ILifecycleProgressMonitor;
import com.sitewhere.spi.server.lifecycle.LifecycleComponentType;

/**
 * Implementation of {@link IDeviceEventManagement} that stores events in Apache
 * Cassandra.
 * 
 * @author Derek
 */
public class CassandraDeviceEventManagement extends TenantEngineLifecycleComponent implements IDeviceEventManagement {

    /** Static logger instance */
    private static Log LOGGER = LogFactory.getLog(CassandraDeviceEventManagement.class);

    /** Configured Cassandra client */
    private CassandraClient client;

    public CassandraDeviceEventManagement() {
	super(LifecycleComponentType.DataStore);
    }

    /*
     * @see
     * com.sitewhere.server.lifecycle.LifecycleComponent#start(com.sitewhere.spi.
     * server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void start(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	if (getClient() == null) {
	    throw new SiteWhereException("No Cassandra client configured.");
	}
	getClient().start(monitor);
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceEventBatch(
     * java.util.UUID, com.sitewhere.spi.device.event.IDeviceEventBatch)
     */
    @Override
    public IDeviceEventBatchResponse addDeviceEventBatch(UUID deviceAssignmentId, IDeviceEventBatch batch)
	    throws SiteWhereException {
	IDeviceAssignment assignment = assertDeviceAssignmentById(deviceAssignmentId);
	return DeviceEventManagementPersistence.deviceEventBatchLogic(assignment, batch, this);
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#getDeviceEventById(java
     * .util.UUID, java.util.UUID)
     */
    @Override
    public IDeviceEvent getDeviceEventById(UUID deviceId, UUID eventId) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * getDeviceEventByAlternateId(java.util.UUID, java.lang.String)
     */
    @Override
    public IDeviceEvent getDeviceEventByAlternateId(UUID deviceId, String alternateId) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#listDeviceEvents(java.
     * util.UUID, com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceEvent> listDeviceEvents(UUID deviceAssignmentId, IDateRangeSearchCriteria criteria)
	    throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * streamDeviceAssignmentCreateEvents()
     */
    @Override
    public Processor<IDeviceAssignmentEventCreateRequest, IEventStreamAck> streamDeviceAssignmentCreateEvents() {
	return new DeviceAssignmentEventCreateProcessor(this);
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceMeasurements(
     * java.util.UUID,
     * com.sitewhere.spi.device.event.request.IDeviceMeasurementsCreateRequest)
     */
    @Override
    public IDeviceMeasurements addDeviceMeasurements(UUID deviceAssignmentId, IDeviceMeasurementsCreateRequest request)
	    throws SiteWhereException {
	IDeviceAssignment assignment = assertDeviceAssignmentById(deviceAssignmentId);
	DeviceMeasurements mxs = DeviceEventManagementPersistence.deviceMeasurementsCreateLogic(request, assignment);
	return mxs;
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceMeasurementsForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceMeasurements> listDeviceMeasurementsForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceMeasurementsForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceMeasurements> listDeviceMeasurementsForAreas(List<UUID> areaIds,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceLocation(java.
     * util.UUID,
     * com.sitewhere.spi.device.event.request.IDeviceLocationCreateRequest)
     */
    @Override
    public IDeviceLocation addDeviceLocation(UUID deviceAssignmentId, IDeviceLocationCreateRequest request)
	    throws SiteWhereException {
	IDeviceAssignment assignment = assertDeviceAssignmentById(deviceAssignmentId);
	DeviceLocation location = DeviceEventManagementPersistence.deviceLocationCreateLogic(assignment, request);

	int bucket = (int) (System.currentTimeMillis() / (60 * 60 * 1000));
	getLogger().info("Using bucket: " + bucket);

	// Build insert for event by id.
	BoundStatement eventById = getClient().getInsertDeviceLocationById().bind();
	CassandraDeviceLocation.bindFields(getClient(), eventById, location);
	process(eventById, location);

	// Build insert for event by assignment.
	BoundStatement eventByAssn = getClient().getInsertDeviceLocationByAssignment().bind();
	CassandraDeviceLocation.bindFields(getClient(), eventByAssn, location);
	eventByAssn.setInt("bucket", bucket);
	process(eventByAssn, location);

	// Build insert for event by area.
	BoundStatement eventByArea = getClient().getInsertDeviceLocationByArea().bind();
	CassandraDeviceLocation.bindFields(getClient(), eventByArea, location);
	eventByArea.setInt("bucket", bucket);
	process(eventByArea, location);

	// Build insert for event by asset.
	BoundStatement eventByAsset = getClient().getInsertDeviceLocationByAsset().bind();
	CassandraDeviceLocation.bindFields(getClient(), eventByAsset, location);
	eventByAsset.setInt("bucket", bucket);
	process(eventByAsset, location);

	return location;
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceLocationsForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceLocation> listDeviceLocationsForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceLocationsForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceLocation> listDeviceLocationsForAreas(List<UUID> areaIds,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceAlert(java.
     * util.UUID, com.sitewhere.spi.device.event.request.IDeviceAlertCreateRequest)
     */
    @Override
    public IDeviceAlert addDeviceAlert(UUID deviceAssignmentId, IDeviceAlertCreateRequest request)
	    throws SiteWhereException {
	IDeviceAssignment assignment = assertDeviceAssignmentById(deviceAssignmentId);
	DeviceAlert alert = DeviceEventManagementPersistence.deviceAlertCreateLogic(assignment, request);
	return alert;
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceAlertsForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceAlert> listDeviceAlertsForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceAlertsForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceAlert> listDeviceAlertsForAreas(List<UUID> areaIds, IDateRangeSearchCriteria criteria)
	    throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceStreamData(
     * java.util.UUID, com.sitewhere.spi.device.streaming.IDeviceStream,
     * com.sitewhere.spi.device.event.request.IDeviceStreamDataCreateRequest)
     */
    @Override
    public IDeviceStreamData addDeviceStreamData(UUID deviceAssignmentId, IDeviceStream stream,
	    IDeviceStreamDataCreateRequest request) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#getDeviceStreamData(
     * java.util.UUID, java.lang.String, long)
     */
    @Override
    public IDeviceStreamData getDeviceStreamData(UUID deviceAssignmentId, String streamId, long sequenceNumber)
	    throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceStreamDataForAssignment(java.util.UUID, java.lang.String,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceStreamData> listDeviceStreamDataForAssignment(UUID assignmentId, String streamId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * addDeviceCommandInvocation(java.util.UUID,
     * com.sitewhere.spi.device.event.request.IDeviceCommandInvocationCreateRequest)
     */
    @Override
    public IDeviceCommandInvocation addDeviceCommandInvocation(UUID deviceAssignmentId,
	    IDeviceCommandInvocationCreateRequest request) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceCommandInvocationsForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceCommandInvocation> listDeviceCommandInvocationsForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceCommandInvocationsForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceCommandInvocation> listDeviceCommandInvocationsForAreas(List<UUID> areaIds,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceCommandInvocationResponses(java.util.UUID, java.util.UUID)
     */
    @Override
    public ISearchResults<IDeviceCommandResponse> listDeviceCommandInvocationResponses(UUID deviceId, UUID invocationId)
	    throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * addDeviceCommandResponse(java.util.UUID,
     * com.sitewhere.spi.device.event.request.IDeviceCommandResponseCreateRequest)
     */
    @Override
    public IDeviceCommandResponse addDeviceCommandResponse(UUID deviceAssignmentId,
	    IDeviceCommandResponseCreateRequest request) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceCommandResponsesForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceCommandResponse> listDeviceCommandResponsesForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceCommandResponsesForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceCommandResponse> listDeviceCommandResponsesForAreas(List<UUID> areaIds,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see
     * com.sitewhere.spi.device.event.IDeviceEventManagement#addDeviceStateChange(
     * java.util.UUID,
     * com.sitewhere.spi.device.event.request.IDeviceStateChangeCreateRequest)
     */
    @Override
    public IDeviceStateChange addDeviceStateChange(UUID deviceAssignmentId, IDeviceStateChangeCreateRequest request)
	    throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceStateChangesForAssignment(java.util.UUID,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceStateChange> listDeviceStateChangesForAssignment(UUID assignmentId,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /*
     * @see com.sitewhere.spi.device.event.IDeviceEventManagement#
     * listDeviceStateChangesForAreas(java.util.List,
     * com.sitewhere.spi.search.IDateRangeSearchCriteria)
     */
    @Override
    public ISearchResults<IDeviceStateChange> listDeviceStateChangesForAreas(List<UUID> areaIds,
	    IDateRangeSearchCriteria criteria) throws SiteWhereException {
	throw new SiteWhereException("Not implemented.");
    }

    /**
     * Process a Cassandra query and handle failures.
     * 
     * @param statement
     * @param event
     * @throws SiteWhereException
     */
    protected void process(BoundStatement statement, IDeviceEvent event) throws SiteWhereException {
	ResultSetFuture future = getClient().getSession().executeAsync("SELECT release_version FROM system.local");
	Futures.addCallback(future, new FutureCallback<ResultSet>() {
	    /*
	     * @see
	     * com.google.common.util.concurrent.FutureCallback#onSuccess(java.lang.Object)
	     */
	    @Override
	    public void onSuccess(ResultSet result) {
	    }

	    /*
	     * @see com.google.common.util.concurrent.FutureCallback#onFailure(java.lang.
	     * Throwable)
	     */
	    @Override
	    public void onFailure(Throwable t) {
		getLogger().error("Failed to persist Cassandra event: " + event.getId(), t);
	    }
	}, MoreExecutors.directExecutor());
    }

    /*
     * @see com.sitewhere.spi.server.lifecycle.ILifecycleComponent#getLogger()
     */
    @Override
    public Log getLogger() {
	return LOGGER;
    }

    /**
     * Assert that a device assignment exists and throw an exception if not.
     * 
     * @param token
     * @return
     * @throws SiteWhereException
     */
    protected IDeviceAssignment assertDeviceAssignmentById(UUID id) throws SiteWhereException {
	IDeviceAssignment assignment = getDeviceManagement().getDeviceAssignment(id);
	if (assignment == null) {
	    throw new SiteWhereSystemException(ErrorCode.InvalidDeviceAssignmentId, ErrorLevel.ERROR);
	}
	return assignment;
    }

    protected IDeviceManagement getDeviceManagement() {
	return ((IEventManagementMicroservice) getTenantEngine().getMicroservice()).getDeviceManagementApiDemux()
		.getApiChannel();
    }

    protected CassandraClient getClient() {
	return client;
    }

    protected void setClient(CassandraClient client) {
	this.client = client;
    }
}