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
package org.structr.cloud.message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.structr.cloud.CloudConnection;
import org.structr.common.error.FrameworkException;

/**
 *
 * @author Christian Morgner
 */
public class End extends Message {

	public End() {}

	@Override
	public void onRequest(CloudConnection serverConnection) throws IOException, FrameworkException {

		serverConnection.commitTransaction();
		serverConnection.endTransaction();

		serverConnection.send(this);
	}

	@Override
	public void onResponse(CloudConnection clientConnection) throws IOException, FrameworkException {
		clientConnection.close();
	}

	@Override
	public void afterSend(CloudConnection connection) {
	}

	@Override
	protected void deserializeFrom(DataInputStream inputStream) throws IOException {
		// no additional data
	}

	@Override
	protected void serializeTo(DataOutputStream outputStream) throws IOException {
		// no additional data
	}
}
