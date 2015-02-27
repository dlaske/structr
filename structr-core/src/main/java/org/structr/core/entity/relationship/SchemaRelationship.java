/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
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
package org.structr.core.entity.relationship;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.helpers.Predicate;
import org.neo4j.helpers.collection.Iterables;
import org.structr.common.CaseHelper;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.ManyToMany;
import org.structr.core.entity.Relation;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.TransactionCommand;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;
import org.structr.schema.ReloadSchema;
import org.structr.schema.Schema;
import org.structr.schema.SchemaHelper;
import org.structr.schema.action.ActionEntry;
import org.structr.schema.action.Actions;
import org.structr.schema.parser.Validator;

/**
 *
 * @author Christian Morgner
 */
public class SchemaRelationship extends ManyToMany<SchemaNode, SchemaNode> implements Schema {

	private static final Logger logger                      = Logger.getLogger(SchemaRelationship.class.getName());
	private static final Pattern ValidKeyPattern            = Pattern.compile("[a-zA-Z_]+");

	public static final Property<String>  name                = new StringProperty("name").indexed();
	public static final Property<String>  relationshipType    = new StringProperty("relationshipType");
	public static final Property<String>  sourceMultiplicity  = new StringProperty("sourceMultiplicity");
	public static final Property<String>  targetMultiplicity  = new StringProperty("targetMultiplicity");
	public static final Property<String>  sourceNotion        = new StringProperty("sourceNotion");
	public static final Property<String>  targetNotion        = new StringProperty("targetNotion");
	public static final Property<String>  sourceJsonName      = new StringProperty("sourceJsonName");
	public static final Property<String>  targetJsonName      = new StringProperty("targetJsonName");
	public static final Property<String>  extendsClass        = new StringProperty("extendsClass").indexed();
	public static final Property<Long>    cascadingDeleteFlag = new LongProperty("cascadingDeleteFlag");
	public static final Property<Long>    autocreationFlag    = new LongProperty("autocreationFlag");

	// internal, do not use externally
	public static final Property<String>  sourceTypeName      = new StringProperty("__internal_Structr_sourceTypeName");


	public static final View defaultView = new View(SchemaRelationship.class, PropertyView.Public,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag
	);

	public static final View uiView = new View(SchemaRelationship.class, PropertyView.Ui,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag
	);

	private Set<String> dynamicViews = new LinkedHashSet<>();

	@Override
	public Class<SchemaNode> getSourceType() {
		return SchemaNode.class;
	}

	@Override
	public Class<SchemaNode> getTargetType() {
		return SchemaNode.class;
	}

	@Override
	public Property<String> getSourceIdProperty() {
		return sourceId;
	}

	@Override
	public Property<String> getTargetIdProperty() {
		return targetId;
	}

	@Override
	public String name() {
		return "IS_RELATED_TO";
	}

	@Override
	public int getCascadingDeleteFlag() {
		return Relation.NONE;
	}

	@Override
	public int getAutocreationFlag() {
		return Relation.SOURCE_TO_TARGET;
	}

	@Override
	public Iterable<PropertyKey> getPropertyKeys(final String propertyView) {

		final Set<PropertyKey> propertyKeys = new LinkedHashSet<>(Iterables.toList(super.getPropertyKeys(propertyView)));

		// add "custom" property keys as String properties
		for (final String key : SchemaHelper.getProperties(getRelationship())) {

			final PropertyKey newKey = new StringProperty(key);
			newKey.setDeclaringClass(getClass());

			propertyKeys.add(newKey);
		}

		return propertyKeys;
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this, relationshipType, errorBuffer);

		return !error && super.isValid(errorBuffer);
	}

	@Override
	public boolean onCreation(SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onCreation(securityContext, errorBuffer)) {

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;
		}

		return false;
	}

	@Override
	public boolean onModification(SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onModification(securityContext, errorBuffer)) {

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;
		}

		return false;
	}

	@Override
	public boolean onDeletion(SecurityContext securityContext, ErrorBuffer errorBuffer, PropertyMap properties) throws FrameworkException {

		if (super.onDeletion(securityContext, errorBuffer, properties)) {

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;

		}

		return false;

	}



	@Override
	public void onRelationshipDeletion() {

		Services.getInstance().getConfigurationProvider().unregisterEntityType(getClassName());

		final String signature = getResourceSignature();
		final String inverseSignature = getInverseResourceSignature();

		if (StringUtils.isNotBlank(signature) && StringUtils.isNotBlank(inverseSignature)) {

			SchemaHelper.removeDynamicGrants(signature);
			SchemaHelper.removeDynamicGrants(inverseSignature);
		}
	}

	// ----- interface Schema -----
	@Override
	public String getClassName() {

		String name = getProperty(AbstractNode.name);
		if (name == null) {

			final String _sourceType = getSchemaNodeSourceType();
			final String _targetType = getSchemaNodeTargetType();
			final String _relType    = getRelationshipType();

			name = _sourceType + _relType + _targetType;
		}

		return name;
	}

	@Override
	public String getMultiplicity(String propertyNameToCheck) {
		return null;
	}

	@Override
	public String getRelatedType(String propertyNameToCheck) {
		return null;
	}

	public String getPropertySource(final String propertyName, final boolean outgoing) {
		return getPropertySource(propertyName, outgoing, false);
	}

	public String getPropertySource(final String propertyName, final boolean outgoing, final boolean newStatementOnly) {

		final StringBuilder buf          = new StringBuilder();
		//final String _sourceJsonName     = getProperty(sourceJsonName);
		//final String _targetJsonName     = getProperty(targetJsonName);
		final String _sourceMultiplicity = getProperty(sourceMultiplicity);
		final String _targetMultiplicity = getProperty(targetMultiplicity);
		final String _sourceNotion       = getProperty(sourceNotion);
		final String _targetNotion       = getProperty(targetNotion);
		final String _sourceType         = getSchemaNodeSourceType();
		final String _targetType         = getSchemaNodeTargetType();
		final String _className          = getClassName();

		if (outgoing) {

			if ("1".equals(_targetMultiplicity)) {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<").append(_targetType).append("> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new EndNode<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_sourceType, _targetNotion));
				buf.append(newStatementOnly ? ")" : ");\n");

			} else {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<java.util.List<").append(_targetType).append(">> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new EndNodes<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_sourceType, _targetNotion));
				buf.append(newStatementOnly ? ")" : ");\n");
			}

		} else {

			if ("1".equals(_sourceMultiplicity)) {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<").append(_sourceType).append("> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new StartNode<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_targetType, _sourceNotion));
				buf.append(newStatementOnly ? ")" : ");\n");

			} else {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<java.util.List<").append(_sourceType).append(">> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new StartNodes<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_targetType, _sourceNotion));
				buf.append(newStatementOnly ? ")" : ");\n");
			}
		}

		return buf.toString();
	}

	public String getMultiplicity(final boolean outgoing) {

		if (outgoing) {

			return getProperty(targetMultiplicity);

		} else {

			return getProperty(sourceMultiplicity);
		}
	}

	public String getPropertyName(final String relatedClassName, final Set<String> existingPropertyNames, final boolean outgoing) {

		String propertyName = "";

		final String relationshipTypeName = getProperty(SchemaRelationship.relationshipType).toLowerCase();
		final String _sourceType          = getSchemaNodeSourceType();
		final String _targetType          = getSchemaNodeTargetType();

		if (outgoing) {

			final String _targetJsonName     = getProperty(targetJsonName);

			if (_targetJsonName != null) {

				// FIXME: no automatic creation?
				propertyName = _targetJsonName;

			} else {

				final String _targetMultiplicity = getProperty(targetMultiplicity);

				if ("1".equals(_targetMultiplicity)) {

					propertyName = CaseHelper.toLowerCamelCase(relationshipTypeName) + CaseHelper.toUpperCamelCase(_targetType);

				} else {

					propertyName = CaseHelper.plural(CaseHelper.toLowerCamelCase(relationshipTypeName) + CaseHelper.toUpperCamelCase(_targetType));
				}
			}

		} else {

			final String _sourceJsonName     = getProperty(sourceJsonName);

			if (_sourceJsonName != null) {
				propertyName = _sourceJsonName;
			} else {

				final String _sourceMultiplicity = getProperty(sourceMultiplicity);

				if ("1".equals(_sourceMultiplicity)) {

					propertyName = CaseHelper.toLowerCamelCase(_sourceType) + CaseHelper.toUpperCamelCase(relationshipTypeName);

				} else {

					propertyName = CaseHelper.plural(CaseHelper.toLowerCamelCase(_sourceType) + CaseHelper.toUpperCamelCase(relationshipTypeName));
				}
			}
		}

		if (existingPropertyNames.contains(propertyName)) {

			// First level: Add direction suffix
			propertyName += outgoing ? "Out" : "In";
			int i=0;

			// New name still exists: Add number
			while (existingPropertyNames.contains(propertyName)) {
				propertyName += ++i;
			}

		}

		existingPropertyNames.add(propertyName);

		return propertyName;
	}

	@Override
	public String getSource(final ErrorBuffer errorBuffer) throws FrameworkException {

		final Map<Actions.Type, List<ActionEntry>> actions = new LinkedHashMap<>();
		final Map<String, Set<String>> viewProperties      = new LinkedHashMap<>();
		final StringBuilder src                            = new StringBuilder();
		final Class baseType                               = AbstractRelationship.class;
		final String _className                            = getClassName();
		final String _sourceNodeType                       = getSchemaNodeSourceType();
		final String _targetNodeType                       = getSchemaNodeTargetType();
		final Set<String> propertyNames                    = new LinkedHashSet<>();
		final Set<Validator> validators                    = new LinkedHashSet<>();
		final Set<String> enums                            = new LinkedHashSet<>();

		src.append("package org.structr.dynamic;\n\n");

		SchemaHelper.formatImportStatements(src, baseType);

		src.append("public class ").append(_className).append(" extends ").append(getBaseType());

		if ("OWNS".equals(getProperty(relationshipType))) {
			src.append(" implements org.structr.core.entity.relationship.Ownership");
		}

		src.append(" {\n\n");

		src.append(SchemaHelper.extractProperties(this, propertyNames, validators, enums, viewProperties, actions, errorBuffer));

		// source and target id properties
		src.append("\tpublic static final Property<String> sourceIdProperty = new SourceId(\"sourceId\");\n");
		src.append("\tpublic static final Property<String> targetIdProperty = new TargetId(\"targetId\");\n");

		// add sourceId and targetId to view properties
		//SchemaHelper.addPropertyToView(PropertyView.Public, "sourceId", viewProperties);
		//SchemaHelper.addPropertyToView(PropertyView.Public, "targetId", viewProperties);

		SchemaHelper.addPropertyToView(PropertyView.Ui, "sourceId", viewProperties);
		SchemaHelper.addPropertyToView(PropertyView.Ui, "targetId", viewProperties);

		// output possible enum definitions
		for (final String enumDefition : enums) {
			src.append(enumDefition);
		}

		for (Map.Entry<String, Set<String>> entry :viewProperties.entrySet()) {

			final String viewName  = entry.getKey();
			final Set<String> view = entry.getValue();

			if (!view.isEmpty()) {
				dynamicViews.add(viewName);
				SchemaHelper.formatView(src, _className, viewName, viewName, view);
			}
		}

		// abstract method implementations
		src.append("\n\t@Override\n");
		src.append("\tpublic Class<").append(_sourceNodeType).append("> getSourceType() {\n");
		src.append("\t\treturn ").append(_sourceNodeType).append(".class;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Class<").append(_targetNodeType).append("> getTargetType() {\n");
		src.append("\t\treturn ").append(_targetNodeType).append(".class;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Property<String> getSourceIdProperty() {\n");
		src.append("\t\treturn sourceId;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Property<String> getTargetIdProperty() {\n");
		src.append("\t\treturn targetId;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic String name() {\n");
		src.append("\t\treturn \"").append(getRelationshipType()).append("\";\n");
		src.append("\t}\n\n");

		SchemaHelper.formatValidators(src, validators);
		SchemaHelper.formatSaveActions(src, actions);

		formatRelationshipFlags(src);

		src.append("}\n");

		return src.toString();
	}

	@Override
	public Set<String> getViews() {
		return dynamicViews;
	}

	@Override
	public String getAuxiliarySource() throws FrameworkException {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String sourceNodeType             = getSchemaNodeSourceType();
		final String targetNodeType             = getSchemaNodeTargetType();
		final StringBuilder src                 = new StringBuilder();
		final String _className                 = getClassName();
		final Class baseType                    = AbstractRelationship.class;

		if (!"File".equals(sourceNodeType) && !"File".equals(targetNodeType)) {
			return null;
		}

		src.append("package org.structr.dynamic;\n\n");

		SchemaHelper.formatImportStatements(src, baseType);

		src.append("public class _").append(_className).append("Helper {\n\n");

		src.append("\n\tstatic {\n\n");
		src.append("\t\tfinal PropertyKey outKey = ");
		src.append(getPropertySource(getPropertyName(sourceNodeType, existingPropertyNames, true), true, true));
		src.append(";\n");
		src.append("\t\toutKey.setDeclaringClass(").append(sourceNodeType).append(".class);\n\n");
		src.append("\t\tfinal PropertyKey inKey = ");
		src.append(getPropertySource(getPropertyName(targetNodeType, existingPropertyNames, false), false, true));
		src.append(";\n");
		src.append("\t\tinKey.setDeclaringClass(").append(targetNodeType).append(".class);\n\n");
		src.append("\t\tStructrApp.getConfiguration().registerDynamicProperty(");
		src.append(sourceNodeType).append(".class, outKey);\n");
		src.append("\t\tStructrApp.getConfiguration().registerDynamicProperty(");
		src.append(targetNodeType).append(".class, inKey);\n\n");
		src.append("\t\tStructrApp.getConfiguration().registerPropertySet(").append(sourceNodeType).append(".class, PropertyView.Ui, outKey);\n");
		src.append("\t\tStructrApp.getConfiguration().registerPropertySet(").append(targetNodeType).append(".class, PropertyView.Ui, inKey);\n");
		src.append("\t}\n");
		src.append("}\n");

		return src.toString();

//		return null;
	}

	// ----- public methods -----
	public String getSchemaNodeSourceType() {
		return getSourceNode().getProperty(SchemaNode.name);
	}

	public String getSchemaNodeTargetType() {
		return getTargetNode().getProperty(SchemaNode.name);
	}

	public String getResourceSignature() {

		final String _sourceType = getSchemaNodeSourceType();
		final String _targetType = getSchemaNodeTargetType();

		return _sourceType + "/" + _targetType;
	}

	public String getInverseResourceSignature() {

		final String _sourceType = getSchemaNodeSourceType();
		final String _targetType = getSchemaNodeTargetType();

		return _targetType + "/" + _sourceType;
	}

	// ----- private methods -----
	private String getRelationshipType() {

		String relType = getProperty(relationshipType);
		if (relType == null) {

			final String _sourceType = getSchemaNodeSourceType().toUpperCase();
			final String _targetType = getSchemaNodeTargetType().toUpperCase();

			relType = _sourceType + "_" + _targetType;
		}

		return relType;
	}
	private String getNotion(final String _className, final String notionSource) {

		final StringBuilder buf = new StringBuilder();

		if (StringUtils.isNotBlank(notionSource)) {

			final Set<String> keys = new LinkedHashSet<>(Arrays.asList(notionSource.split("[\\s,]+")));
			if (!keys.isEmpty()) {

				if (keys.size() == 1) {

					String key     = keys.iterator().next();
					boolean create = key.startsWith("+");

					if (create) {
						key = key.substring(1);
					}

					if (ValidKeyPattern.matcher(key).matches()) {

						buf.append(", new PropertyNotion(");
						buf.append(getNotionKey(_className, key));
						buf.append(", ").append(create);
						buf.append(")");

					} else {

						logger.log(Level.WARNING, "Invalid key name {0} for notion.", key);
					}

				} else {

					buf.append(", new PropertySetNotion(");

					// use only matching keys
					for (final Iterator<String> it = Iterables.filter(new KeyMatcher(), keys).iterator(); it.hasNext();) {

						buf.append(getNotionKey(_className, it.next()));

						if (it.hasNext()) {
							buf.append(", ");
						}
					}

					buf.append(")");
				}
			}
		}

		return buf.toString();
	}

	private String getNotionKey(final String _className, final String key) {
		return _className + "." + key;
	}

	private String getBaseType() {

		final String _sourceMultiplicity = getProperty(sourceMultiplicity);
		final String _targetMultiplicity = getProperty(targetMultiplicity);
		final String _sourceType         = getSchemaNodeSourceType();
		final String _targetType         = getSchemaNodeTargetType();
		final StringBuilder buf          = new StringBuilder();

		if ("1".equals(_sourceMultiplicity)) {

			if ("1".equals(_targetMultiplicity)) {

				buf.append("OneToOne");

			} else {

				buf.append("OneToMany");
			}

		} else {

			if ("1".equals(_targetMultiplicity)) {

				buf.append("ManyToOne");

			} else {

				buf.append("ManyToMany");
			}
		}

		buf.append("<");
		buf.append(_sourceType);
		buf.append(", ");
		buf.append(_targetType);
		buf.append(">");

		return buf.toString();
	}

	// ----- interface Syncable -----
	@Override
	public List<GraphObject> getSyncData() {

		final List<GraphObject> syncables = super.getSyncData();

		syncables.add(getSourceNode());
		syncables.add(getTargetNode());

		return syncables;
	}

	// ----- private methods -----
	private void formatRelationshipFlags(final StringBuilder src) {

		Long cascadingDelete = getProperty(cascadingDeleteFlag);
		if (cascadingDelete != null) {

			src.append("\n\t@Override\n");
			src.append("\tpublic int getCascadingDeleteFlag() {\n");

			switch (cascadingDelete.intValue()) {

				case Relation.ALWAYS :
					src.append("\t\treturn Relation.ALWAYS;\n");
					break;

				case Relation.CONSTRAINT_BASED :
					src.append("\t\treturn Relation.CONSTRAINT_BASED;\n");
					break;

				case Relation.SOURCE_TO_TARGET :
					src.append("\t\treturn Relation.SOURCE_TO_TARGET;\n");
					break;

				case Relation.TARGET_TO_SOURCE :
					src.append("\t\treturn Relation.TARGET_TO_SOURCE;\n");
					break;

				case Relation.NONE :

				default :
					src.append("\t\treturn Relation.NONE;\n");

			}

			src.append("\t}\n\n");
		}

		Long autocreate = getProperty(autocreationFlag);
		if (autocreate != null) {

			src.append("\n\t@Override\n");
			src.append("\tpublic int getAutocreationFlag() {\n");

			switch (autocreate.intValue()) {

				case Relation.ALWAYS :
					src.append("\t\treturn Relation.ALWAYS;\n");
					break;

				case Relation.SOURCE_TO_TARGET :
					src.append("\t\treturn Relation.SOURCE_TO_TARGET;\n");
					break;

				case Relation.TARGET_TO_SOURCE :
					src.append("\t\treturn Relation.TARGET_TO_SOURCE;\n");
					break;

				default :
					src.append("\t\treturn Relation.NONE;\n");

			}

			src.append("\t}\n\n");
		}
	}

	// ----- nested classes -----
	private static class KeyMatcher implements Predicate<String> {

		@Override
		public boolean accept(String t) {

			if (ValidKeyPattern.matcher(t).matches()) {
				return true;
			}

			logger.log(Level.WARNING, "Invalid key name {0} for notion.", t);

			return false;
		}
	}
}
