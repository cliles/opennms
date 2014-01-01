/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import org.jolokia.client.J4pClient;
import org.jolokia.client.request.*;
import org.jolokia.client.exception.*;

import org.apache.log4j.Level;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.poller.NetworkInterfaceNotSupportedException;

/**
 * This class is designed to be used by the service poller framework to test the
 * availability of a generic mbean method on remote interfaces via a jolokia agent. The class
 * implements the ServiceMonitor interface that allows it to be used along with
 * other plug-ins by the service poller framework.
 *
 * @author cliles
 */

@Distributable
final public class JolokiaBeanMonitor extends AbstractServiceMonitor {

    /**
     * Default port.
     */
    private static final int DEFAULT_PORT = 8080;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000; // 3 second timeout on
                                                        // read()

    public static final String PARAMETER_BANNER = "banner";
    public static final String PARAMETER_PORT = "port";
    public static final String PARAMETER_BEANNAME = "beanname";
    public static final String PARAMETER_METHODNAME = "methodname";
    public static final String PARAMETER_METHODINPUT1 = "input1";
    public static final String PARAMETER_METHODINPUT2 = "input2";

    public static final String DEFAULT_METHODINPUT = "default";

    /**
     * {@inheritDoc}
     *
     * Poll the specified address for service availability.
     *
     * During the poll an attempt is made to execute the named method (with optional input) connect on the specified port. If
     * the exec on request is successful, the banner line generated by the
     * interface is parsed and if the banner text indicates that we are talking
     * to Provided that the interface's response is valid we set the service
     * status to SERVICE_AVAILABLE and return.
     */
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        NetworkInterface<InetAddress> iface = svc.getNetInterface();

        //
        // Process parameters
        //

        //
        // Get interface address from NetworkInterface
        //
        if (iface.getType() != NetworkInterface.TYPE_INET)
          throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_INET currently supported");

        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        // Port
        //
        int port = ParameterMap.getKeyedInteger(parameters, PARAMETER_PORT, DEFAULT_PORT);

        //BeanName
        //
        String strBeanName = ParameterMap.getKeyedString(parameters, PARAMETER_BEANNAME, null);

        //MethodName
        //
        String strMethodName = ParameterMap.getKeyedString(parameters, PARAMETER_METHODNAME, null);

        //Optional Inputs
        //
        String strInput1 = ParameterMap.getKeyedString(parameters, PARAMETER_METHODINPUT1, DEFAULT_METHODINPUT);
        String strInput2 = ParameterMap.getKeyedString(parameters, PARAMETER_METHODINPUT2, DEFAULT_METHODINPUT);

        // BannerMatch
        //
        String strBannerMatch = ParameterMap.getKeyedString(parameters, PARAMETER_BANNER, null);

        // Get the address instance.
        //
        InetAddress ipv4Addr = (InetAddress) iface.getAddress();

        final String hostAddress = InetAddressUtils.str(ipv4Addr);
		    if (log().isDebugEnabled()) {
          log().debug("poll: address = " + hostAddress + ", port = " + port + ", " + tracker);
        }

        // Give it a whirl
        //
        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            try {
                tracker.startAttempt();

                J4pClient j4pClient = new J4pClient("http://"+hostAddress+":"+port+"/jolokia");
                j4pClient.connectionTimeout(tracker.getSoTimeout());
                log().debug("JolokiaBeanMonitor: connected to host: " + ipv4Addr + " on port: " + port);

                // We're connected, so upgrade status to unresponsive
                serviceStatus = PollStatus.unresponsive();

                if (strBannerMatch == null || strBannerMatch.length() == 0 || strBannerMatch.equals("*")) {
                    serviceStatus = PollStatus.available(tracker.elapsedTimeInMillis());
                    break;
                }

                J4pExecRequest execReq;

                //Default Inputs
                if (strInput1.equals("default") && strInput2.equals("default")) {
                  log().debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName);
                  execReq = new J4pExecRequest(strBeanName, strMethodName);
                }
                else if (!strInput1.equals("default") && strInput2.equals("default")) {
                //Single Input
                  log().debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName + " args: " + strInput1);
                  execReq = new J4pExecRequest(strBeanName, strMethodName, strInput1);
                }
                else {
                //Double Input
                  log().debug("JolokiaBeanMonitor - execute bean: " + strBeanName + " method: " + strMethodName + " args: " + strInput1 + " " + strInput2);
                  execReq = new J4pExecRequest(strBeanName, strMethodName, strInput1, strInput2);
                }

                execReq.setPreferredHttpMethod("POST");
                J4pExecResponse resp = j4pClient.execute(execReq);

                double responseTime = tracker.elapsedTimeInMillis();

                String response = resp.getValue().toString();

                if (response == null)
                  continue;
                if (log().isDebugEnabled()) {
                  log().debug("poll: banner = " + response);
                  log().debug("poll: responseTime= " + responseTime + "ms");
                }


                //Could it be a regex?
                if (strBannerMatch.charAt(0)=='~'){
                  if (!response.matches(strBannerMatch.substring(1)))
                    serviceStatus = PollStatus.unavailable("Banner does not match Regex '"+strBannerMatch+"'");
                  else
                    serviceStatus = PollStatus.available(responseTime);
                }
                else {
                  if (response.indexOf(strBannerMatch) > -1)
                    serviceStatus = PollStatus.available(responseTime);
                  else
                    serviceStatus = PollStatus.unavailable("Did not find expected Text '"+strBannerMatch+"'");
                }

            } catch (J4pConnectException e) {
                serviceStatus = logDown(Level.WARN, "Connection exception for address: " + ipv4Addr + ":" + port, e);
                break;
            } catch (J4pRemoteException e) {
                serviceStatus = logDown(Level.DEBUG, "Remote exception: " + e);
            } catch (Exception e) {
                serviceStatus = logDown(Level.DEBUG, "Exception: " + e);
            }
        }

        //
        // return the status of the service
        //
        return serviceStatus;
    }

}
