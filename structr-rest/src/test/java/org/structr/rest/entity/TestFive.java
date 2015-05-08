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
package org.structr.rest.entity;

import java.util.List;
import org.structr.common.PropertyView;
import org.structr.common.View;
import static org.structr.core.GraphObject.id;
import org.structr.core.entity.AbstractNode;
import org.structr.core.notion.PropertyNotion;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.Property;
import org.structr.core.property.StartNode;

/**
 *
 * @author Axel Morgner
 */
public class TestFive extends AbstractNode {
	
	public static final Property<List<TestOne>> manyToManyTestOnes = new EndNodes<>("manyToManyTestOnes", FiveOneManyToMany.class, new PropertyNotion(id));
	public static final Property<List<TestOne>> oneToManyTestOnes  = new EndNodes<>("oneToManyTestOnes",  FiveOneOneToMany.class, new PropertyNotion(id));
	
	public static final Property<TestThree>     oneToOneTestThree  = new EndNode<>("oneToOneTestThree",  FiveThreeOneToOne.class);
	public static final Property<TestThree>     manyToOneTestThree = new StartNode<>("manyToOneTestThree", ThreeFiveOneToMany.class);
	
	public static final View defaultView = new View(TestFive.class, PropertyView.Public, AbstractNode.name, manyToManyTestOnes, oneToManyTestOnes, oneToOneTestThree, manyToOneTestThree);
}
