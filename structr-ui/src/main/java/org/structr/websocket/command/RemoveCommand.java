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
package org.structr.websocket.command;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.graph.NodeInterface;
import org.structr.web.entity.dom.DOMNode;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.w3c.dom.DOMException;

/**
 *
 * @author Christian Morgner
 * @author Axel Morgner
 */
public class RemoveCommand extends AbstractCommand {

	static {

		StructrWebSocket.addCommand(RemoveCommand.class);

	}

	private static final Logger logger = Logger.getLogger(RemoveCommand.class.getName());

	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final SecurityContext securityContext = getWebSocket().getSecurityContext();
		String id = webSocketData.getId();

		if (id != null) {

			final NodeInterface node = getNode(id);

			if (node != null) {

				if (node instanceof DOMNode) {

					// Use new DOM interface
					DOMNode domNode = (DOMNode) node;

					try {
	
						domNode.getParentNode().removeChild(domNode);

						// Remove node from page
						domNode.setProperty(DOMNode.syncedNodes, Collections.EMPTY_LIST);
						domNode.setProperty(DOMNode.pageId, null);

					} catch (DOMException | FrameworkException ex) {

						logger.log(Level.SEVERE, "Could not remove node from page " + domNode, ex);
						getWebSocket().send(MessageBuilder.status().code(422).message(ex.getMessage()).build(), true);

					}

				} else {

					final App app = StructrApp.getInstance(securityContext);

					try {

						// Old style: Delete all incoming CONTAINS rels
						for (AbstractRelationship rel : node.getIncomingRelationships()) {

							if ("CONTAINS".equals(rel.getType())) {

								app.delete(rel);

							}

						}

					} catch (Throwable t) {

						logger.log(Level.SEVERE, "Could not delete relationship", t);
						getWebSocket().send(MessageBuilder.status().code(400).message("Error in RemoveCommand: " + t.getMessage()).build(), true);

					}
				}

			} else {

				getWebSocket().send(MessageBuilder.status().code(404).build(), true);
			}

		} else {

			getWebSocket().send(MessageBuilder.status().code(400).message("RemoveCommand called with empty id").build(), true);
		}

	}

	@Override
	public String getCommand() {

		return "REMOVE";

	}

}
