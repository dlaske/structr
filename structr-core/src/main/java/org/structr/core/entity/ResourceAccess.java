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
package org.structr.core.entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.relationship.Access;
import org.structr.core.notion.PropertySetNotion;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.EndNodes;
import org.structr.core.property.IntProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;
import org.structr.core.validator.TypeUniquenessValidator;

/**
 * Controls access to REST resources.
 *
 * Objects of this class act as a doorkeeper for REST resources
 * that match the signature string in the 'signature' field.
 * <p>
 * A ResourceAccess object defines access granted
 * <ul>
 * <li>to everyone (public)
 * <li>to authenticated principals
 * <li>to invidual principals (when connected to a {link @Principal} node
 * </ul>
 *
 * <p>'flags' is a sum of any of the following values:
 *
 *  FORBIDDEN             = 0
 *  AUTH_USER_GET         = 1
 *  AUTH_USER_PUT         = 2
 *  AUTH_USER_POST        = 4
 *  AUTH_USER_DELETE      = 8
 *  NON_AUTH_USER_GET     = 16
 *  NON_AUTH_USER_PUT     = 32
 *  NON_AUTH_USER_POST    = 64
 *  NON_AUTH_USER_DELETE  = 128
 *  AUTH_USER_OPTIONS     = 256
 *  NON_AUTH_USER_OPTIONS = 512
 *
 * @author Christian Morgner
 * @author Axel Morgner
 */
public class ResourceAccess extends AbstractNode {

	private static final Map<String, ResourceAccess> grantCache = new ConcurrentHashMap<>();
	private static final Logger logger                          = Logger.getLogger(ResourceAccess.class.getName());

	public static final Property<String>               signature          = new StringProperty("signature", new TypeUniquenessValidator(ResourceAccess.class)).indexed();
	public static final Property<Long>                 flags              = new LongProperty("flags").indexed();
	public static final Property<Integer>              position           = new IntProperty("position").indexed();
	public static final Property<List<PropertyAccess>> propertyAccess     = new EndNodes<>("propertyAccess", Access.class, new PropertySetNotion(id, name));
	public static final Property<Boolean>              isResourceAccess   = new BooleanProperty("isResourceAccess").defaultValue(true).readOnly();

	public static final View uiView = new View(ResourceAccess.class, PropertyView.Ui,
		signature, flags, position, isResourceAccess
	);

	public static final View publicView = new View(ResourceAccess.class, PropertyView.Public,
		signature, flags, isResourceAccess
	);

	// non-static members
	private String cachedResourceSignature = null;
	private Long cachedFlags               = null;
	private Integer cachedPosition         = null;

	@Override
	public String toString() {

		StringBuilder buf = new StringBuilder();

		buf.append("('").append(getResourceSignature()).append("', ").append(flags.jsonName()).append(": ").append(getFlags()).append("', ").append(position.jsonName()).append(": ").append(getPosition()).append(")");

		return buf.toString();
	}

	public boolean hasFlag(long flag) {
		return (getFlags() & flag) == flag;
	}

	public void setFlag(final long flag) throws FrameworkException {

		// reset cached field
		cachedFlags = null;

		// set modified property
		setProperty(ResourceAccess.flags, getFlags() | flag);
	}

	public void clearFlag(final long flag) throws FrameworkException {

		// reset cached field
		cachedFlags = null;

		// set modified property
		setProperty(ResourceAccess.flags, getFlags() & ~flag);
	}

	public long getFlags() {

		if (cachedFlags == null) {
			cachedFlags = getProperty(ResourceAccess.flags);
		}

		if (cachedFlags != null) {
			return cachedFlags.longValue();
		}

		return 0;
	}

	public String getResourceSignature() {

		if (cachedResourceSignature == null) {
			cachedResourceSignature = getProperty(ResourceAccess.signature);
		}

		return cachedResourceSignature;
	}

	public int getPosition() {

		if (cachedPosition == null) {
			cachedPosition = getProperty(ResourceAccess.position);
		}

		if (cachedPosition != null) {
			return cachedPosition;
		}

		return 0;
	}

	@Override
	public boolean onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}

	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer) {
		return isValid(errorBuffer);
	}

	@Override
	public boolean onDeletion(SecurityContext securityContext, ErrorBuffer errorBuffer, PropertyMap properties) {
		grantCache.clear();
		return true;
	}

	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this,  ResourceAccess.signature, errorBuffer);
		error |= ValidationHelper.checkPropertyNotNull(this, ResourceAccess.flags, errorBuffer);

		return !error;
	}

	@Override
	public void afterCreation(SecurityContext securityContext) {
		grantCache.clear();
	}

	@Override
	public void afterModification(SecurityContext securityContext) {
		grantCache.clear();
	}

	public static ResourceAccess findGrant(final SecurityContext securityContext, final String signature) throws FrameworkException {

		ResourceAccess grant = grantCache.get(signature);
		if (grant == null) {

			//grant = StructrApp.getInstance(securityContext).nodeQuery(ResourceAccess.class).and(ResourceAccess.signature, signature).getFirst();
			// ignore security context for now, so ResourceAccess objects don't have to be visible
			grant = StructrApp.getInstance().nodeQuery(ResourceAccess.class).and(ResourceAccess.signature, signature).getFirst();
			if (grant != null) {

				grantCache.put(signature, grant);

			} else {

				logger.log(Level.FINE, "No resource access object found for {0}", signature);
			}
		}

		return grant;
	}
}
