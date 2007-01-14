
//This file is part of the OpenNMS(R) Application.

//OpenNMS(R) is Copyright (C) 2006 The OpenNMS Group, Inc.  All rights reserved.
//OpenNMS(R) is a derivative work, containing both original code, included code and modified
//code that was published under the GNU General Public License. Copyrights for modified
//and included code are below.

//OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.

//Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.

//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

//For more information contact:
//OpenNMS Licensing       <license@opennms.org>
//http://www.opennms.org/
//http://www.opennms.com/

package org.opennms.netmgt.poller.remote.support;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.model.OnmsMonitoringLocationDefinition;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.model.OnmsLocationMonitor.MonitorStatus;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.remote.ConfigurationChangedListener;
import org.opennms.netmgt.poller.remote.PollService;
import org.opennms.netmgt.poller.remote.PolledService;
import org.opennms.netmgt.poller.remote.Poller;
import org.opennms.netmgt.poller.remote.PollerBackEnd;
import org.opennms.netmgt.poller.remote.PollerConfiguration;
import org.opennms.netmgt.poller.remote.PollerFrontEnd;
import org.opennms.netmgt.poller.remote.PollerSettings;
import org.opennms.netmgt.poller.remote.ServicePollState;
import org.opennms.netmgt.poller.remote.ServicePollStateChangedEvent;
import org.opennms.netmgt.poller.remote.ServicePollStateChangedListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

public class DefaultPollerFrontEnd implements PollerFrontEnd, InitializingBean,
DisposableBean {

    // injected dependencies
    private PollerBackEnd m_backEnd;

    private LinkedList<ConfigurationChangedListener> m_configChangeListeners = new LinkedList<ConfigurationChangedListener>();

    // state variables
    private boolean m_initialized;

    // current configuration
    private PollerConfiguration m_pollerConfiguration;

    private PollerSettings m_pollerSettings;

    private PollService m_pollService;

    // current state of polled services
    private Map<Integer, ServicePollState> m_pollState = new LinkedHashMap<Integer, ServicePollState>();

    // listeners
    private LinkedList<PropertyChangeListener> m_propertyChangeListeners = new LinkedList<PropertyChangeListener>();

    private LinkedList<ServicePollStateChangedListener> m_servicePollStateChangedListeners = new LinkedList<ServicePollStateChangedListener>();

    private boolean m_started;

    public void addConfigurationChangedListener(ConfigurationChangedListener l) {
        m_configChangeListeners.addFirst(l);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        m_propertyChangeListeners.addFirst(l);
    }

    public void addServicePollStateChangedListener(
            ServicePollStateChangedListener l) {
        m_servicePollStateChangedListeners.addFirst(l);
    }

    public void afterPropertiesSet() throws Exception {
        assertNotNull(m_backEnd, "pollerBackEnd");
        assertNotNull(m_pollService, "pollService");
        assertNotNull(m_pollerSettings, "pollerSettings");
        m_initialized = true;

        if (isRegistered()) {
            initializePollState();
        }

    }

    public void checkConfig() {
        if (!isRegistered()) {
            // no reason to check if we aren't registerd
            return;
        }

        assertConfigured();
        if (m_backEnd.pollerCheckingIn(getMonitorId(), m_pollerConfiguration
                                       .getConfigurationTimestamp()) == MonitorStatus.CONFIG_CHANGED) {
            initializePollState();
        }
    }

    public void destroy() throws Exception {
        if (isRegistered()) {
            stop();
        }
    }

    public Map<String, String> getDetails() {
        HashMap<String, String> details = new HashMap<String, String>();

        Properties p = System.getProperties();

        for (Map.Entry<Object, Object> e : p.entrySet()) {
            if (e.getKey().toString().startsWith("os.") && e.getValue() != null) {
                details.put(e.getKey().toString(), e.getValue().toString());
            }
        }

        try {
            InetAddress us = InetAddress.getLocalHost();
            details.put("org.opennms.netmgt.poller.remote.hostAddress", us
                        .getHostAddress());
            details.put("org.opennms.netmgt.poller.remote.hostName", us
                        .getHostName());
        } catch (UnknownHostException e) {
            // do nothing
        }

        return details;
    }

    public int getMonitorId() {
        return m_pollerSettings.getMonitorId();
    }

    public Collection<OnmsMonitoringLocationDefinition> getMonitoringLocations() {
        return m_backEnd.getMonitoringLocations();
    }

    public String getMonitorName() {
        return (isRegistered() ? m_backEnd.getMonitorName(getMonitorId()) : "");
    }

    public Collection<PolledService> getPolledServices() {
        assertRegistered();
        return Arrays.asList(m_pollerConfiguration.getPolledServices());
    }

    public List<ServicePollState> getPollerPollState() {
        synchronized (m_pollState) {
            return new LinkedList<ServicePollState>(m_pollState.values());
        }
    }

    public ServicePollState getServicePollState(int polledServiceId) {
        assertRegistered();
        synchronized (m_pollState) {
            return m_pollState.get(polledServiceId);
        }
    }

    public boolean isRegistered() {
        return m_pollerSettings.getMonitorId() != null;
    }

    public boolean isStarted() {
        return m_started;
    }

    public void pollService(Integer polledServiceId) {
        assertRegistered();

        PollStatus result = doPoll(polledServiceId);
        if (result == null)
            return;

        updateServicePollState(polledServiceId, result);

        m_backEnd.reportResult(getMonitorId(), polledServiceId, result);

    }

    public void register(String monitoringLocation) {
        assertInitialized();
        int monitorId = m_backEnd.registerLocationMonitor(monitoringLocation);
        m_pollerSettings.setMonitorId(monitorId);
        initializePollState();
        firePropertyChange("registered", false, true);
    }

    public void removeConfigurationChangedListener(
            ConfigurationChangedListener l) {
        m_configChangeListeners.remove(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        m_propertyChangeListeners.remove(l);
    }

    public void removeServicePollStateChangedListener(
            ServicePollStateChangedListener l) {
        m_servicePollStateChangedListeners.remove(l);
    }

    public void setInitialPollTime(Integer polledServiceId, Date initialPollTime) {
        assertRegistered();
        ServicePollState pollState = getServicePollState(polledServiceId);
        pollState.setInitialPollTime(initialPollTime);
        fireServicePollStateChanged(pollState.getPolledService(), pollState
                                    .getIndex());
    }

    public void setPollerBackEnd(PollerBackEnd backEnd) {
        m_backEnd = backEnd;
    }

    public void setPollerSettings(PollerSettings settings) {
        m_pollerSettings = settings;
    }

    public void setPollService(PollService pollService) {
        m_pollService = pollService;
    }

    public void stop() {
        m_backEnd.pollerStopping(getMonitorId());
        m_started = false;
    }

    private void assertConfigured() {
        assertRegistered();
        Assert.notNull(m_pollerConfiguration,
        "The poller has not been configured");
    }

    private void assertInitialized() {
        Assert.isTrue(m_initialized, "afterProperties set has not been called");
    }

    private void assertNotNull(Object propertyValue, String propertyName) {
        Assert.state(propertyValue != null, propertyName
                     + " must be set for instances of " + Poller.class);
    }

    private void assertRegistered() {
        assertInitialized();
        Assert
        .state(isRegistered(),
        "The poller must be registered before we can poll or get its configuration");
    }

    private PollStatus doPoll(Integer polledServiceId) {
        assertRegistered();

        PolledService polledService = getPolledService(polledServiceId);
        if (polledService == null) {
            return null;
        }
        PollStatus result = m_pollService.poll(polledService);
        return result;
    }

    private void fireConfigurationChange(Date oldTime, Date newTime) {
        PropertyChangeEvent e = new PropertyChangeEvent(this, "configuration",
                                                        oldTime, newTime);
        for (ConfigurationChangedListener l : m_configChangeListeners) {
            l.configurationChanged(e);
        }
    }

    private void firePropertyChange(String propertyName, Object oldValue,
            Object newValue) {
        PropertyChangeEvent e = new PropertyChangeEvent(this, propertyName,
                                                        oldValue, newValue);

        for (PropertyChangeListener l : m_propertyChangeListeners) {
            l.propertyChange(e);
        }
    }

    private void fireServicePollStateChanged(PolledService polledService,
            int index) {
        ServicePollStateChangedEvent e = new ServicePollStateChangedEvent(
                                                                          polledService, index);

        for (ServicePollStateChangedListener l : m_servicePollStateChangedListeners) {
            l.pollStateChange(e);
        }
    }

    private PolledService getPolledService(Integer polledServiceId) {
        assertRegistered();
        ServicePollState servicePollState = getServicePollState(polledServiceId);
        return (servicePollState == null ? null : servicePollState
            .getPolledService());
    }

    private void initializePollState() {
        Date oldTime = (m_pollerConfiguration == null ? null
            : m_pollerConfiguration.getConfigurationTimestamp());

        if (!isStarted()) {
            start();
        }

        m_pollService.setServiceMonitorLocators(m_backEnd
                                                .getServiceMonitorLocators(DistributionContext.REMOTE_MONITOR));

        m_pollerConfiguration = m_backEnd
        .getPollerConfiguration(getMonitorId());

        synchronized (m_pollState) {

            int i = 0;
            m_pollState.clear();
            for (PolledService service : m_pollerConfiguration
                    .getPolledServices()) {
                m_pollService.initialize(service);
                m_pollState.put(service.getServiceId(), new ServicePollState(
                                                                             service, i++));
            }
        }

        fireConfigurationChange(oldTime, m_pollerConfiguration
                                .getConfigurationTimestamp());
    }

    private Category log() {
        return ThreadCategory.getInstance(getClass());
    }

    private void start() {
        assertRegistered();
        if (!m_backEnd.pollerStarting(getMonitorId(), getDetails())) {
            m_pollerSettings.setMonitorId(null);
            throw new IllegalStateException(
            "Monitor no longers exists on server.  You need to reregister");
        }
        m_started = true;
    }

    private void updateServicePollState(Integer polledServiceId,
            PollStatus result) {
        assertRegistered();

        ServicePollState pollState = getServicePollState(polledServiceId);
        pollState.setLastPoll(result);
        fireServicePollStateChanged(pollState.getPolledService(), pollState
                                    .getIndex());
    }

}
