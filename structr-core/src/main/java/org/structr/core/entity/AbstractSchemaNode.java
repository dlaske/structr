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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.relationship.SchemaNodeMethod;
import org.structr.core.entity.relationship.SchemaNodeProperty;
import org.structr.core.entity.relationship.SchemaNodeView;
import org.structr.core.graph.NodeAttribute;
import static org.structr.core.graph.NodeInterface.name;
import org.structr.core.graph.TransactionCommand;
import org.structr.core.graph.TransactionPostProcess;
import org.structr.core.property.EndNodes;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StringProperty;
import org.structr.core.validator.TypeUniquenessValidator;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.Schema;

/**
 *
 * @author Christian Morgner
 */
public abstract class AbstractSchemaNode extends SchemaReloadingNode implements Schema {

	public static final Property<List<SchemaProperty>> schemaProperties = new EndNodes<>("schemaProperties", SchemaNodeProperty.class);
	public static final Property<List<SchemaMethod>>   schemaMethods    = new EndNodes<>("schemaMethods", SchemaNodeMethod.class);
	public static final Property<List<SchemaView>>     schemaViews      = new EndNodes<>("schemaViews", SchemaNodeView.class);
	public static final Property<String>               icon             = new StringProperty("icon");
	public static final Set<String> hiddenPropertyNames                 = new LinkedHashSet<>();

	public static final View defaultView = new View(AbstractSchemaNode.class, PropertyView.Public,
		name, icon
	);

	public static final View uiView = new View(AbstractSchemaNode.class, PropertyView.Ui,
		name, schemaProperties, schemaViews, schemaMethods, icon
	);

	public static final View schemaView = new View(AbstractSchemaNode.class, "schema",
		id, type, name, schemaProperties, schemaViews, schemaMethods, icon
	);

	public static final View exportView = new View(SchemaMethod.class, "export",
		id, type, name, icon
	);

	static {

		AbstractNode.name.addValidator(new TypeUniquenessValidator<String>(AbstractSchemaNode.class));

		hiddenPropertyNames.add("visibilityStartDate");
		hiddenPropertyNames.add("visibilityEndDate");
		hiddenPropertyNames.add("createdBy");
		hiddenPropertyNames.add("hidden");
		hiddenPropertyNames.add("deleted");
	}

	@Override
	public boolean onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onCreation(securityContext, errorBuffer)) {

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("createDefaultProperties", new CreateBuiltInSchemaEntities(this));

			return true;
		}

		return false;
	}

	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onModification(securityContext, errorBuffer)) {

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("createDefaultProperties", new CreateBuiltInSchemaEntities(this));

			return true;
		}

		return false;
	}

	@Override
	public List<SchemaProperty> getSchemaProperties() {
		return getProperty(AbstractSchemaNode.schemaProperties);
	}

	@Override
	public List<SchemaView> getSchemaViews() {
		return getProperty(AbstractSchemaNode.schemaViews);
	}

	@Override
	public List<SchemaMethod> getSchemaMethods() {
		return getProperty(AbstractSchemaNode.schemaMethods);
	}

	public void createBuiltInSchemaEntities(final ErrorBuffer errorBuffer) throws FrameworkException {
		new CreateBuiltInSchemaEntities(this).execute(securityContext, errorBuffer);
	}

	private static class CreateBuiltInSchemaEntities implements TransactionPostProcess {

		private AbstractSchemaNode node = null;

		public CreateBuiltInSchemaEntities(final AbstractSchemaNode node) {
			this.node = node;
		}

		@Override
		public boolean execute(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

			final ConfigurationProvider config = StructrApp.getConfiguration();
			final Set<String> viewNames        = new HashSet<>();

			// do not create a node for the "all" view
			viewNames.add("all");

			// create set of existing views
			for (final SchemaView view : node.getProperty(schemaViews)) {
				viewNames.add(view.getProperty(AbstractNode.name));
			}
			// determine runtime type
			Class builtinClass = config.getNodeEntityClass(node.getClassName());
			if (builtinClass == null) {

				// second try: relationship class name
				builtinClass = config.getRelationshipEntityClass(node.getClassName());
			}

			if (builtinClass != null) {

				for (final String view : config.getPropertyViewsForType(builtinClass)) {

					if (!viewNames.contains(view)) {

						final Set<String> viewPropertyNames   = new HashSet<>();
						final List<SchemaProperty> properties = new LinkedList<>();

						// collect names of properties in the given view
						for (final PropertyKey key : config.getPropertySet(builtinClass, view)) {
							viewPropertyNames.add(key.jsonName());
						}

						// collect schema properties that match the view
						for (final SchemaProperty schemaProperty : node.getProperty(SchemaNode.schemaProperties)) {

							final String schemaPropertyName = schemaProperty.getProperty(SchemaProperty.name);
							if (viewPropertyNames.contains(schemaPropertyName)) {

								properties.add(schemaProperty);
							}
						}

						// create view node
						StructrApp.getInstance(node.getSecurityContext()).create(SchemaView.class,
							new NodeAttribute(SchemaView.schemaNode, node),
							new NodeAttribute(SchemaView.name, view),
							new NodeAttribute(SchemaView.schemaProperties, properties),
							new NodeAttribute(SchemaView.isBuiltinView, true)
						);
					}
				}
			}

			return true;
		}
	}
}
