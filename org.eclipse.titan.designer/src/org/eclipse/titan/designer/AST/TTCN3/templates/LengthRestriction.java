/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.templates;

import org.eclipse.titan.designer.AST.ASTNode;
import org.eclipse.titan.designer.AST.GovernedSimple.CodeSectionType;
import org.eclipse.titan.designer.AST.ILocateableNode;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.Module;
import org.eclipse.titan.designer.AST.NULL_Location;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.IIncrementallyUpdateable;
import org.eclipse.titan.designer.AST.TTCN3.values.ArrayDimension;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * Represents a range restriction.
 *
 * @author Kristof Szabados
 * */
public abstract class LengthRestriction extends ASTNode implements ILocateableNode, IIncrementallyUpdateable {

	/**
	 * The location of the whole restriction. This location encloses the
	 * restriction fully, as it is used to report errors to.
	 **/
	private Location location;

	public LengthRestriction() {
		location = NULL_Location.INSTANCE;
	}

	@Override
	/** {@inheritDoc} */
	public final void setLocation(final Location location) {
		this.location = location;
	}

	@Override
	/** {@inheritDoc} */
	public final Location getLocation() {
		return location;
	}

	/**
	 * Creates and returns a string representation if the length
	 * restriction.
	 *
	 * @return the string representation of the length restriction.
	 * */
	public abstract String createStringRepresentation();

	/**
	 * Sets the code_section attribute of this length restriction to the provided value.
	 *
	 * @param codeSection the code section where this length restriction should be generated.
	 * */
	public abstract void setCodeSection(final CodeSectionType codeSection);

	/**
	 * Check that the length restriction is a correct value, and at is
	 * allowed at a given location.
	 *
	 * @param timestamp
	 *                the time stamp of the actual semantic check cycle.
	 * @param expectedValue
	 *                the value kind expected.
	 * */
	public abstract void check(final CompilationTimeStamp timestamp, final Expected_Value_type expectedValue);

	/**
	 * Checks if the length restriction is valid for the array type.
	 *
	 * @param timestamp
	 *                the time stamp of the actual semantic check cycle.
	 * @param dimension
	 *                the dimension of the array type.
	 * */
	public abstract void checkArraySize(final CompilationTimeStamp timestamp, final ArrayDimension dimension);

	/**
	 * Checks if the provided amount of elements can be valid or not
	 * according to this length restriction.
	 *
	 * @param timestamp
	 *                the time stamp of the actual semantic check cycle.
	 * @param nofElements
	 *                the number of elements the checked template has.
	 * @param lessAllowed
	 *                wheter less elements should be accepted (subset
	 *                template)
	 * @param moreAllowed
	 *                wheter more elements should be accepted (the template
	 *                has anyornone elements).
	 * @param hasAnyornone
	 *                whether the template has anyornone elements.
	 * @param locatable
	 *                the location errors should be reported to if found.
	 * */
	public abstract void checkNofElements(final CompilationTimeStamp timestamp, final int nofElements, boolean lessAllowed,
			final boolean moreAllowed, final boolean hasAnyornone, final ILocateableNode locatable);

	/**
	 * Handles the incremental parsing of this length restriction.
	 *
	 * @param reparser
	 *                the parser doing the incremental parsing.
	 * @param isDamaged
	 *                true if the location contains the damaged area, false
	 *                if only its' location needs to be updated.
	 * */
	@Override
	public abstract void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException;

	/**
	 * Walks through the template recursively and appends the java
	 * initialization sequence of all (directly or indirectly) referenced
	 * non-parameterized templates and the default values of all
	 * parameterized templates to source and returns the resulting string.
	 * Only objects belonging to module usageModule are initialized.
	 *
	 * @param aData the structure to put imports into and get temporal variable names from.
	 * @param source the source for code generated
	 * @param usageModule the name where the restriction is used
	 * */
	public abstract void reArrangeInitCode(final JavaGenData aData, final StringBuilder source, final Module usageModule);

	/**
	 * Add generated java code for length restriction.
	 *
	 * @param aData the structure to put imports into and get temporal variable names from.
	 * @param source the source for code generated
	 * @param name the name to init
	 */
	public abstract void generateCodeInit( final JavaGenData aData, final StringBuilder source, final String name );

}
