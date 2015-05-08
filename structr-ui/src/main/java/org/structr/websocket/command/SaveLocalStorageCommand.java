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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.structr.common.SecurityContext;
import org.structr.web.entity.User;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;

/**
 * @author Axel Morgner
 */
public class SaveLocalStorageCommand extends AbstractCommand {

	private static final Logger logger = Logger.getLogger(SaveLocalStorageCommand.class.getName());

	static {

		StructrWebSocket.addCommand(SaveLocalStorageCommand.class);
	}

	@Override
	public void processMessage(WebSocketMessage webSocketData) {

		final Map<String, Object> nodeData = webSocketData.getNodeData();
		final String localStorageString = (String) nodeData.get("localStorageString");
		final SecurityContext securityContext = getWebSocket().getSecurityContext();

		if (StringUtils.isNotBlank(localStorageString)) {

			try {

				securityContext.getUser(false).setProperty(User.localStorage, localStorageString);

			} catch (Throwable t) {

				logger.log(Level.WARNING, t.toString());
				t.printStackTrace();

				// send exception
				getWebSocket().send(MessageBuilder.status().code(422).message(t.toString()).build(), true);
			}

		} else {

			// send exception
			getWebSocket().send(MessageBuilder.status().code(422).message("Cannot save localStorage").build(), true);
		}

	}

	@Override
	public String getCommand() {

		return "SAVE_LOCAL_STORAGE";

	}

}
