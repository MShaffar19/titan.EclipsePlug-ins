/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titanium.markers.spotters.implementation;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.titan.designer.AST.IVisitableNode;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Altstep;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Function;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Testcase;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Definition;
import org.eclipse.titanium.markers.spotters.BaseModuleCodeSmellSpotter;
import org.eclipse.titanium.markers.types.CodeSmellType;

public class UncommentedDefinition extends BaseModuleCodeSmellSpotter {

	public UncommentedDefinition() {
		super(CodeSmellType.UNCOMMENTED_FUNCTION);
	}

	@Override
	public void process(final IVisitableNode node, final Problems problems) {
		if (node instanceof Definition) {
			final Definition s = (Definition) node;
			if (s.getCommentLocation() == null) {
				final String msg = MessageFormat.format("The {0} {1} should have a comment", s.getAssignmentName(),
						s.getIdentifier().getDisplayName());
				problems.report(s.getIdentifier().getLocation(), msg);
			}
		}
	}

	@Override
	public List<Class<? extends IVisitableNode>> getStartNode() {
		final List<Class<? extends IVisitableNode>> ret = new ArrayList<Class<? extends IVisitableNode>>(3);
		ret.add(Def_Altstep.class);
		ret.add(Def_Function.class);
		ret.add(Def_Testcase.class);
		return ret;
	}

}
