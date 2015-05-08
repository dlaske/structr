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
package org.structr.rest.resource;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;
import org.structr.core.Result;
import org.structr.core.property.ArrayProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StringProperty;
import org.structr.rest.RestMethodResult;
import org.structr.rest.exception.IllegalMethodException;
import org.structr.rest.exception.IllegalPathException;

/**
 *
 * @author Axel Morgner
 */
public class EnvResource extends Resource {

	public enum UriPart {
		_env
	}

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) throws FrameworkException {

		this.securityContext = securityContext;

		if (UriPart._env.name().equals(part)) {

			return true;
		}

		return false;
	}

	@Override
	public Result doGet(PropertyKey sortKey, boolean sortDescending, int pageSize, int page, String offsetId) throws FrameworkException {

		List<GraphObjectMap> resultList = new LinkedList<>();

		GraphObjectMap info = new GraphObjectMap();
		
		final String classPath = System.getProperty("java.class.path");

		final Pattern outerPattern = Pattern.compile("(structr-.+?(?=.jar))");
		Matcher outerMatcher = outerPattern.matcher(classPath);
		
		List<Map<String, String>> modules = new LinkedList<>();
		
		while (outerMatcher.find()) {
			
			final String g = outerMatcher.group();

			final Pattern innerPattern = Pattern.compile("(structr-core|structr-rest|structr-ui)-(.*)-([0-9a-f]{0,40}).*");
			final Matcher innerMatcher = innerPattern.matcher(g);
			
			final Map<String, String> module = new HashMap<>();
			
			if (innerMatcher.matches()) {
			
				module.put("module", innerMatcher.group(1));
				module.put("version", innerMatcher.group(2));
				module.put("build", innerMatcher.group(3));
				
			}

			modules.add(module);
		}

		info.setProperty(new StringProperty("modules"), modules);
		
		info.setProperty(new StringProperty("classPath"), classPath);

		resultList.add(info);

		return new Result(resultList, resultList.size(), false, false);
	}

	@Override
	public RestMethodResult doPost(Map<String, Object> propertySet) throws FrameworkException {
		throw new IllegalMethodException();
	}

	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {

		throw new IllegalPathException();
	}

	@Override
	public String getUriPart() {
		return getResourceSignature();
	}

	@Override
	public Class getEntityClass() {
		return null;
	}

	@Override
	public String getResourceSignature() {
		return UriPart._env.name();
	}

	@Override
	public boolean isCollectionResource() throws FrameworkException {
		return false;
	}
}
