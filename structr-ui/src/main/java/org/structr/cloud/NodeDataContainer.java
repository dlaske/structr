/*
 *  Copyright (C) 2011 Axel Morgner, structr <structr@structr.org>
 *
 *  This file is part of structr <http://structr.org>.
 *
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.cloud;

import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.NodeInterface;

/**
 * Serializable data container for a node to be transported over network.
 *
 * To be initialized with {@link AbstractNode} in constructor.
 *
 * @author axel
 */
public class NodeDataContainer extends DataContainer {

	protected String sourceNodeId = null;
	protected Class type          = null;

	public NodeDataContainer() {}

	public NodeDataContainer(final NodeInterface node) throws FrameworkException {

		type         = node.getClass();
		sourceNodeId = node.getUuid();
		//Map properties = new HashMap<String, Object>();

		collectProperties(node.getNode());
	}

	/**
	 * Return id of node in source instance
	 *
	 * @return
	 */
	public String getSourceNodeId() {
		return sourceNodeId;
	}

	public Class getType() {
		return type;
	}

	@Override
	public Message process(final ServerContext context) {

		context.storeNode(this);

		return new AckPacket("Node");
	}
}
