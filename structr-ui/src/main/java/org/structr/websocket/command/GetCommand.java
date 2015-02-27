/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.websocket.command;

import java.util.Arrays;
import java.util.logging.Logger;
import org.structr.core.entity.AbstractNode;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command to retrieve a single node by id.
 *
 * @author Christian Morgner
 * @author Axel Morgner
 */
public class GetCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(GetCommand.class.getName());

	static {

		StructrWebSocket.addCommand(GetCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		AbstractNode node = getNode(webSocketData.getId());

		if (node != null) {
			webSocketData.setResult(Arrays.asList(node));

			// send only over local connection (no broadcast)
			getWebSocket().send(webSocketData, true);

		} else {

			//logger.log(Level.WARNING, "Node not found for id {0}!", webSocketData.getId());
			// Not necessary to send a 404 here
			//getWebSocket().send(MessageBuilder.status().code(404).build(), true);

		}
	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {
		return "GET";
	}
}
