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
package org.structr.media;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.LinkedHashMap;
import java.util.Map;
import org.structr.common.SecurityContext;
import org.structr.web.entity.VideoFile;

/**
 *
 * @author Christian Morgner
 */

public class GetVideoInfoProcess extends AbstractProcess<Map<String, Object>> {

	private VideoFile inputVideo = null;

	public GetVideoInfoProcess(final SecurityContext securityContext, final VideoFile inputVideo) {

		super(securityContext);

		this.inputVideo = inputVideo;
	}

	@Override
	public void preprocess() {
	}

	@Override
	public StringBuilder getCommandLine() {

		StringBuilder commandLine = new StringBuilder("avprobe -v verbose -show_format -show_streams -of json ");

		// build command line from builder options
		commandLine.append(inputVideo.getDiskFilePath(securityContext));

		return commandLine;
	}

	@Override
	public Map<String, Object> processExited(int exitCode) {

		if (exitCode == 0) {

			return new GsonBuilder().create().fromJson(outputStream(), new TypeToken<LinkedHashMap<String, Object>>(){}.getType());
		}

		return null;
	}
}

