/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.linkd;


import org.apache.commons.lang.builder.ToStringBuilder;

public class LldpRemInterface {
	
    private final Integer m_lldpRemChassidSubtype;
    private final String m_lldpRemChassisid;
    private final Integer m_lldpRemNodeid;
    private final Integer m_lldpRemIfIndex;
    private final Integer m_lldpLocIfIndex;


    public LldpRemInterface(Integer lldpRemChassidSubtype,
            String lldpRemChassisid, Integer lldpRemNodeid,Integer lldpRemIfIndex,
            Integer lldpLocIfIndex) {
        super();
        m_lldpRemChassidSubtype = lldpRemChassidSubtype;
        m_lldpRemChassisid = lldpRemChassisid;
        m_lldpRemNodeid = lldpRemNodeid;
        m_lldpRemIfIndex = lldpRemIfIndex;
        m_lldpLocIfIndex = lldpLocIfIndex;
    }
	
    
    public Integer getLldpRemNodeid() {
        return m_lldpRemNodeid;
    }


    public Integer getLldpRemChassidSubtype() {
        return m_lldpRemChassidSubtype;
    }


    public String getLldpRemChassisid() {
        return m_lldpRemChassisid;
    }


    public Integer getLldpRemIfIndex() {
        return m_lldpRemIfIndex;
    }

    public Integer getLldpLocIfIndex() {
        return m_lldpLocIfIndex;
    }


    /**
	 * <p>toString</p>
	 *
	 * @return a {@link java.lang.String} object.
	 */
    @Override
	public String toString() {
	    return new ToStringBuilder(this)
	    .append("lldpRemChassidSubtype", m_lldpRemChassidSubtype)
	    .append("lldpRemChassisid", m_lldpRemChassisid)
            .append("lldpRemNodeid", m_lldpRemNodeid)
	    .append("lldpRemIfIndex", m_lldpRemIfIndex)
	    .append("lldpLocIfIndex", m_lldpLocIfIndex)
	    .toString();
	}
}

