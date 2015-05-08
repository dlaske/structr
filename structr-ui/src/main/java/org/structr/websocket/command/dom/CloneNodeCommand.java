/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.websocket.command.dom;

import java.util.Map;

import org.structr.common.error.FrameworkException;
import org.structr.web.entity.dom.DOMNode;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.command.AbstractCommand;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.w3c.dom.DOMException;


/**
 * Clone a DOMNode to an arbitrary location in the same or another document (page).
 *
 * @author Axel Morgner
 */
public class CloneNodeCommand extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(CloneNodeCommand.class);
	}

	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final String id                             = webSocketData.getId();
		final Map<String, Object> nodeData          = webSocketData.getNodeData();
		final String parentId                       = (String) nodeData.get("parentId");
		final boolean deep                          = (Boolean) nodeData.get("deep");
		

		if (id != null) {

			final DOMNode node = getDOMNode(id);

			if (parentId == null) {

				getWebSocket().send(MessageBuilder.status().code(422).message("Cannot clone node without parentId").build(), true);

				return;

			}

			// check if parent node with given ID exists
			final DOMNode parent = getDOMNode(parentId);

			if (parent == null) {

				getWebSocket().send(MessageBuilder.status().code(404).message("Parent node not found").build(), true);

				return;

			}

			try {
				DOMNode clonedNode = (DOMNode) node.cloneNode(deep);
				parent.appendChild(clonedNode);
				clonedNode.setProperty(DOMNode.ownerDocument, parent.getProperty(DOMNode.ownerDocument));

			} catch (DOMException | FrameworkException ex) {

				getWebSocket().send(MessageBuilder.status().code(422).message(ex.getMessage()).build(), true);

			}

		} else {

			getWebSocket().send(MessageBuilder.status().code(422).message("Cannot append node without id").build(), true);
		}

	}

	@Override
	public String getCommand() {

		return "CLONE_NODE";

	}

	
}
