/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.common.geo;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.jxpath.JXPathContext;
import org.structr.core.Services;

/**
 * Abstract base class for geocoding providers.
 *
 * @author Christian Morgner
 */
public abstract class AbstractGeoCodingProvider implements GeoCodingProvider {

	private static final Logger logger = Logger.getLogger(AbstractGeoCodingProvider.class.getName());
	protected String apiKey            = null;
	
	public AbstractGeoCodingProvider() {
		this.apiKey = Services.getInstance().getConfigurationValue(Services.GEOCODING_APIKEY, "");
	}
	
	protected <T> T extract(Map source, String path, Class<T> type) {
		
		JXPathContext context = JXPathContext.newContext(source);
		T value               = (T)context.getValue(path);
		
		return value;
	}
	
	protected String encodeURL(String source) {
		
		try {
			return URLEncoder.encode(source, "UTF-8");
			
		} catch (UnsupportedEncodingException ex) {

			logger.log(Level.WARNING, "Unsupported Encoding", ex);
		}
		
		// fallback, unencoded
		return source;
	}
}
