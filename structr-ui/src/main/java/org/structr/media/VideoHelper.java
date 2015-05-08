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

import java.util.Map;
import java.util.concurrent.Future;
import org.structr.web.entity.VideoFile;

/**
 *
 * @author Christian Morgner
 */
public interface VideoHelper {

	public VideoHelper scale(final VideoFormat format);
	public VideoHelper scale(final int width, final int height);
	public VideoHelper scale(final String customFormat);

	public Future<VideoFile> doConversion();

	public Map<String, String> getMetadata();
	public void setMetadata(final String key, final String value);
	public void setMetadata(final Map<String, String> metadata);

	public Map<String, Object> getVideoInfo();
}
