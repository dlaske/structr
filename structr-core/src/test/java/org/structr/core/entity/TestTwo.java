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

import org.structr.common.error.EmptyPropertyToken;
import org.structr.common.error.ErrorBuffer;
import org.structr.core.property.StartNode;
import org.structr.core.property.Property;

/**
 * A simple entity for the most basic tests.
 * 
 * This class has a not-null constraint on the TestOne object, so when
 * the TestOne object is deleted, this object should be deleted as well.
 * 
 * @author Axel Morgner
 */
public class TestTwo extends AbstractNode {
	
	public static final Property<TestOne> testOne = new StartNode<>("testOne", OneTwoOneToOne.class);
	
	@Override
	public boolean isValid(ErrorBuffer errorBuffer) {
		
		if (getTestOne() == null) {
			
			errorBuffer.add(TestTwo.class.getSimpleName(), new EmptyPropertyToken(testOne));
			
			return false;
		}
		
		return true;
	}
	
	private TestOne getTestOne() {
		return getProperty(testOne);
	}
}
