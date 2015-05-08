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
package org.structr.core;

import java.io.Serializable;
import java.util.Set;

/**
 * A structr module.
 *
 * @author Axel Morgner
 */
public interface Module extends Serializable
{
	/**
	 * @return the path of this module
	 */
	public String getModulePath();

	/**
	 * @return the classes this module contains
	 */
	public Set<String> getClasses();

	/**
	 * @return the properties this module contains
	 */
	public Set<String> getProperties();

	/**
	 * @return the resources this module contains
	 */
	public Set<String> getResources();

	/**
	 * @return the libraries this module contains
	 */
	public Set<String> getLibraries();
}
