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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.Permission;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.property.PropertyKey;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------
/**
 *
 * @author Axel Morgner
 */
public class RemoveFromCollectionCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(RemoveFromCollectionCommand.class.getName());

	static {

		StructrWebSocket.addCommand(RemoveFromCollectionCommand.class);

	}

	//~--- methods --------------------------------------------------------
	@Override
	public void processMessage(WebSocketMessage webSocketData) {


		final String keyString  = (String) webSocketData.getNodeData().get("key");
		if (keyString == null) {

			logger.log(Level.SEVERE, "Unable to remove given object from collection: key is null");
			getWebSocket().send(MessageBuilder.status().code(400).build(), true);

		}

		final String idToRemove = (String) webSocketData.getNodeData().get("idToRemove");
		if (idToRemove == null) {

			logger.log(Level.SEVERE, "Unable to remove given object from collection: idToRemove is null");
			getWebSocket().send(MessageBuilder.status().code(400).build(), true);

		}

		GraphObject obj         = getNode(webSocketData.getId());
		if (obj != null) {

			if (!((AbstractNode)obj).isGranted(Permission.write, getWebSocket().getSecurityContext())) {

				getWebSocket().send(MessageBuilder.status().message("No write permission").code(400).build(), true);
				logger.log(Level.WARNING, "No write permission for {0} on {1}", new Object[]{getWebSocket().getCurrentUser().toString(), obj.toString()});
				return;

			}

		}

		if (obj == null) {

			// No node? Try to find relationship
			obj = getRelationship(webSocketData.getId());
		}

		GraphObject objToRemove = getNode(idToRemove);

		if (obj != null && objToRemove != null) {

			try {

				PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(obj.getClass(), keyString);
				if (key != null) {

					List collection = (List) obj.getProperty(key);
					collection.remove(objToRemove);
					obj.setProperty(key, collection);

				}

			} catch (FrameworkException ex) {

				logger.log(Level.SEVERE, "Unable to set properties: {0}", ((FrameworkException) ex).toString());
				getWebSocket().send(MessageBuilder.status().code(400).build(), true);

			}

		} else {

			logger.log(Level.WARNING, "Graph object with uuid {0} not found.", webSocketData.getId());
			getWebSocket().send(MessageBuilder.status().code(404).build(), true);

		}

	}

	//~--- get methods ----------------------------------------------------
	@Override
	public String getCommand() {

		return "REMOVE_FROM_COLLECTION";

	}

}
