/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titanium.refactoring.semicolon;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

/**
 * Wizard for the 'Add semicolon modifires' operation.
 *
 * @author Farkas Izabella Ingrid
 */

public class AddSemicolonWizard  extends RefactoringWizard implements IExecutableExtension {

	private static final String WIZ_WINDOWTITLE = "Add semicolon modifiers";

	public AddSemicolonWizard(final Refactoring refactoring) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
	}

	@Override
	protected void addUserInputPages() {
		setDefaultPageTitle(WIZ_WINDOWTITLE);
	}

	@Override
	public void setInitializationData(final IConfigurationElement config, final String propertyName, final Object data)
			throws CoreException {
	}

}
