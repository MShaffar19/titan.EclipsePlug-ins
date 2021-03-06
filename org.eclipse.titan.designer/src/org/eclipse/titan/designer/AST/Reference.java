/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.AST.Assignment.Assignment_type;
import org.eclipse.titan.designer.AST.GovernedSimple.CodeSectionType;
import org.eclipse.titan.designer.AST.ISetting.Setting_type;
import org.eclipse.titan.designer.AST.ISubReference.Subreference_type;
import org.eclipse.titan.designer.AST.IType.Type_type;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.ASN1.ASN1Assignment;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Choice_Type;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Sequence_Type;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Set_Type;
import org.eclipse.titan.designer.AST.ASN1.types.Open_Type;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.IIncrementallyUpdateable;
import org.eclipse.titan.designer.AST.TTCN3.definitions.ActualParameterList;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Altstep;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Extfunction;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Function;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Template;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Def_Var;
import org.eclipse.titan.designer.AST.TTCN3.definitions.Definition;
import org.eclipse.titan.designer.AST.TTCN3.definitions.FormalParameter;
import org.eclipse.titan.designer.AST.TTCN3.definitions.FormalParameter.parameterEvaluationType;
import org.eclipse.titan.designer.AST.TTCN3.definitions.FormalParameterList;
import org.eclipse.titan.designer.AST.TTCN3.definitions.IParameterisedAssignment;
import org.eclipse.titan.designer.AST.TTCN3.definitions.LazyFuzzyParamData;
import org.eclipse.titan.designer.AST.TTCN3.types.AbstractOfType;
import org.eclipse.titan.designer.AST.TTCN3.types.Anytype_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.Array_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.CompField;
import org.eclipse.titan.designer.AST.TTCN3.types.ComponentTypeBody;
import org.eclipse.titan.designer.AST.TTCN3.types.Component_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.SequenceOf_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.TTCN3_Set_Seq_Choice_BaseType;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value.Operation_type;
import org.eclipse.titan.designer.AST.TTCN3.values.expressions.ExpressionStruct;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.declarationsearch.Declaration;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * The Reference class represent a general reference used to refer to an element
 * in the source code.
 *
 * @author Kristof Szabados
 * */
public class Reference extends ASTNode implements ILocateableNode, IIncrementallyUpdateable, IReferencingElement {
	private static final String STRINGELEMENTUNUSABLE = "Reference to a string element of type `{0}'' cannot be used in this context";
	private static final String VARIABLEXPECTED = "Reference to a variable or value parameter was expected instead of {0}";
	private static final String ALTSTEPEXPECTED = "Reference to an altstep was expected in the argument instead of {0}";

	private static final String FULLNAMEPART = ".<sub_reference";

	public static final String COMPONENTEXPECTED = "component type expected";
	public static final String TYPEEXPECTED = "Type reference expected";
	public static final String ASN1SETTINGEXPECTED = "Reference to ASN.1 setting expected";

	/**
	 * Module identifier. Might be null. In that case it means, that we were
	 * not able to decide if the reference has a module identifier or not
	 * */
	protected Identifier modid;

	/**
	 * The list of sub-references.
	 * <p>
	 * The first element might be the id of the module until the module id
	 * detection is not executed.
	 * */
	protected ArrayList<ISubReference> subReferences;

	private boolean refersToStringElement = false;

	/**
	 * Stores weather we have already tried to detect the module id.
	 * */
	protected boolean detectedModuleId = false;

	/**
	 * indicates if this reference has already been found erroneous in the
	 * actual checking cycle.
	 */
	private boolean isErroneous;
	/**
	 * The assignment referred to by this reference.
	 * */
	protected Assignment referredAssignment;
	protected CompilationTimeStamp lastTimeChecked;

	/**
	 * Stores whether this reference is used on the left hand side of an
	 * assignment or not.
	 */
	private boolean usedOnLeftSide = false;

	/**
	 * Stores whether this reference is used directly in an isbound call or
	 * not.
	 */
	private boolean usedInIsbound = false;

	public Reference(final Identifier modid) {
		this.modid = modid;
		detectedModuleId = modid != null;
		subReferences = new ArrayList<ISubReference>();
	}

	public Reference(final Identifier modid, final List<ISubReference> subReferences) {
		this.modid = modid;
		detectedModuleId = modid != null;
		this.subReferences = new ArrayList<ISubReference>(subReferences);
		this.subReferences.trimToSize();

		for (int i = 0; i < subReferences.size(); i++) {
			subReferences.get(i).setFullNameParent(this);
		}
	}

	/** @return a new instance of this reference */
	public Reference newInstance() {
		return new Reference(modid, subReferences);
	}

	/**
	 * @return the assignment this reference was last evaluated to point to.
	 *         Might be outdated information.
	 * */
	public Assignment getAssOld() {
		return referredAssignment;
	}

	/**
	 * Sets the scope of the base reference without setting the scope of the
	 * sub references.
	 *
	 * @param scope
	 *                the scope to set.
	 * */
	public void setBaseScope(final Scope scope) {
		super.setMyScope(scope);
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		subReferences.trimToSize();
		for (int i = 0; i < subReferences.size(); i++) {
			subReferences.get(i).setMyScope(scope);
		}
	}

	/**
	 * Sets the code_section attribute of this reference to the provided value.
	 *
	 * @param codeSection the code section where this reference be generated.
	 * */
	public void setCodeSection(final CodeSectionType codeSection) {
		for (int i = 0; i < subReferences.size(); i++) {
			subReferences.get(i).setCodeSection(codeSection);
		}
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		for (int i = 0; i < subReferences.size(); i++) {
			if (child == subReferences.get(i)) {
				return builder.append(FULLNAMEPART).append(String.valueOf(i + 1)).append(INamedNode.MORETHAN);
			}
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setLocation(final Location location) {
		//Do nothing
	}

	// Location is optimized not to store an object at it is not needed
	@Override
	/** {@inheritDoc} */
	public Location getLocation() {
		Location temp;
		if (modid != null) {
			temp = new Location(modid.getLocation());
		} else if (!subReferences.isEmpty()) {
			temp = new Location(subReferences.get(0).getLocation());
		} else {
			return NULL_Location.INSTANCE;
		}

		if (!subReferences.isEmpty()) {
			temp.setEndOffset(subReferences.get(subReferences.size() - 1).getLocation().getEndOffset());
		}

		return temp;
	}

	public final CompilationTimeStamp getLastTimeChecked() {
		return lastTimeChecked;
	}

	/**
	 * Checks if the reference was evaluated to be erroneous in this time
	 * frame.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @return true if the setting is erroneous, or false otherwise
	 * */
	public final boolean getIsErroneous(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return isErroneous;
		}

		return false;
	}

	/**
	 * Sets the erroneousness of the setting.
	 *
	 * @param isErroneous
	 *                set the erroneousness property of the references.
	 * */
	public void setIsErroneous(final boolean isErroneous) {
		this.isErroneous = isErroneous;
	}

	/**
	 * @return whether this reference is used on the left hand side of an
	 *         assignment or not.
	 * */
	public boolean getUsedOnLeftHandSide() {
		return usedOnLeftSide;
	}

	/**
	 * Sets this reference to be used on the left hand side of an
	 * assignment.
	 */
	public void setUsedOnLeftHandSide() {
		usedOnLeftSide = true;
	}

	/**
	 * @return whether this reference is used directly in an isbound call or
	 *         not.
	 * */
	public boolean getUsedInIsbound() {
		return usedInIsbound;
	}

	/** Sets that this reference is used directly inside an isbound call */
	public void setUsedInIsbound() {
		usedInIsbound = true;
	}

	/**
	 * Detects and returns the module identifier.
	 *
	 * @return the module identifier, might be null if the reference did not
	 *         contain one.
	 * */
	public Identifier getModuleIdentifier() {
		detectModid();
		return modid;
	}

	/**
	 * Returns the identifier of the reference.
	 * <p>
	 * Before returning, the module identifier detection is run. After the
	 * module identifier detection, the first sub-reference must be the
	 * identifier of an assignment (if the sub-reference exists).
	 *
	 * @return the identifier contained in this reference
	 * */
	public Identifier getId() {
		detectModid();

		if (!subReferences.isEmpty()) {
			return subReferences.get(0).getId();
		}

		return null;
	}

	/**
	 * @return the sub-references of this reference, with any previous
	 *         checks.
	 * */
	public List<ISubReference> getSubreferences() {
		return subReferences;
	}

	/**
	 * Collects the sub-references of the reference.
	 * <p>
	 * It is important that the name of the reference is the first member of
	 * the list.
	 *
	 * @param from
	 *                the first index to include.
	 * @param till
	 *                the last index to include.
	 * @return the sub-references of this reference, with any previous
	 *         checks.
	 * */
	public List<ISubReference> getSubreferences(final int from, final int till) {
		final List<ISubReference> result = new ArrayList<ISubReference>();

		final int size = Math.min(subReferences.size() - 1, till);
		for (int i = Math.max(0, from); i <= size; i++) {
			result.add(subReferences.get(i));
		}

		return result;
	}

	/**
	 * Adds a sub-reference to the and of the list of sub-references.
	 *
	 * @param subReference
	 *                the sub-reference to be added.
	 * */
	public void addSubReference(final ISubReference subReference) {
		subReferences.add(subReference);
		subReference.setFullNameParent(this);
	}

	/**
	 * Deletes and returns the last sub-reference.
	 *
	 * @return the last sub-reference before the deletion, or null if there
	 *         was none.
	 * */
	public ISubReference removeLastSubReference() {
		if (subReferences.isEmpty()) {
			return null;
		}

		final ISubReference result = subReferences.remove(subReferences.size() - 1);

		if (subReferences.isEmpty() && modid != null) {
			subReferences.add(new FieldSubReference(modid));
			modid = null;
			detectedModuleId = false;
		}

		return result;
	}

	/**
	 * @return true if the reference references a string element, false
	 *         otherwise.
	 * */
	public boolean refersToStringElement() {
		return refersToStringElement;
	}

	/** set the reference to be referencing string elements. */
	public void setStringElementReferencing() {
		refersToStringElement = true;
	}

	/**
	 * clears previous information on whether the reference to be
	 * referencing string elements or not.
	 */
	public void clearStringElementReferencing() {
		refersToStringElement = false;
	}

	/**
	 * Clears the cache of this reference.
	 **/
	public void clear() {
		referredAssignment = null;
		lastTimeChecked = null;
	}

	/**
	 * Tries to detect if the list of sub-references contains the module
	 * identifier.
	 * <p>
	 * In parsing time the module identifier can only be detected /
	 * separated from sub-references, if the reference contains "objid"s.
	 * */
	public void detectModid() {
		if (detectedModuleId || modid != null) {
			return;
		}

		Identifier firstId = null;
		if (!subReferences.isEmpty() && Subreference_type.fieldSubReference.equals(subReferences.get(0).getReferenceType())) {
			firstId = subReferences.get(0).getId();
			if (subReferences.size() > 1 && !Subreference_type.arraySubReference.equals(subReferences.get(1).getReferenceType())) {
				// there are 3 possible situations:
				// 1. first_id points to a local definition
				// (this has the priority)
				// modid: 0, id: first_id
				// 2. first_id points to an imported module
				// (trivial case)
				// modid: first_id, id: second_id
				// 3. none of the above (first_id might be an
				// imported symbol)
				// modid: 0, id: first_id
				// Note: Rule 1 has the priority because it can
				// be overridden using
				// the notation <id>.objid { ... }.<id> but
				// there is no work-around in the reverse way.
				if (myScope != null && !myScope.hasAssignmentWithId(CompilationTimeStamp.getBaseTimestamp(), firstId)
						&& myScope.isValidModuleId(firstId)) {
					// rule 1 is not fulfilled, but rule 2
					// is fulfilled
					modid = firstId;
					subReferences.remove(0);
				}
			}
		}

		detectedModuleId = true;
	}

	/**
	 * IDentifies and returns the referred setting.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @return the setting referenced or an Error_Setting instance in case
	 *         of an error
	 * */
	public ISetting getRefdSetting(final CompilationTimeStamp timestamp) {
		final Assignment assignment = getRefdAssignment(timestamp, true);
		if (assignment != null) {
			return assignment.getSetting(timestamp);
		}

		return new Error_Setting();
	}

	/**
	 * Detects and returns the referred assignment.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param checkParameterList
	 *                whether the parameter list of the reference should be
	 *                checked or not.
	 *
	 * @return the assignment referred by this reference, or null if not
	 *         found.
	 * */
	public Assignment getRefdAssignment(final CompilationTimeStamp timestamp, final boolean checkParameterList) {
		return getRefdAssignment(timestamp, checkParameterList, null);
	}


	public Assignment getRefdAssignment(final CompilationTimeStamp timestamp, final boolean checkParameterList, final IReferenceChain referenceChain) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp) && !checkParameterList) {
			return referredAssignment;
		}

		lastTimeChecked = timestamp;

		if (myScope == null || getId() == null) {
			return null;
		}

		final boolean newChain = null == referenceChain;
		IReferenceChain tempReferenceChain;
		if (newChain) {
			tempReferenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
		} else {
			tempReferenceChain = referenceChain;
		}

		isErroneous = false;
		detectedModuleId = false;
		detectModid();

		referredAssignment = myScope.getAssBySRef(timestamp, this, referenceChain);
		if (referredAssignment == null) {
			isErroneous = true;
			return referredAssignment;
		}

		referredAssignment.check(timestamp, tempReferenceChain);
		referredAssignment.setUsed();

		if (referredAssignment instanceof Definition) {
			final String referingModuleName = getMyScope().getModuleScope().getName();
			if (!((Definition) referredAssignment).referingHere.contains(referingModuleName)) {
				((Definition) referredAssignment).referingHere.add(referingModuleName);
			}
		}

		if (checkParameterList) {
			FormalParameterList formalParameterList = null;

			if (referredAssignment instanceof IParameterisedAssignment) {
				formalParameterList = ((IParameterisedAssignment) referredAssignment).getFormalParameterList();
			}

			if (formalParameterList == null) {
				if (!subReferences.isEmpty() && subReferences.get(0) instanceof ParameterisedSubReference) {
					final String message = MessageFormat.format("The referenced {0} cannot have actual parameters",
							referredAssignment.getDescription());
					getLocation().reportSemanticError(message);
					setIsErroneous(true);
				}
			} else if (!subReferences.isEmpty()) {
				final ISubReference firstSubReference = subReferences.get(0);
				if (firstSubReference instanceof ParameterisedSubReference) {
					formalParameterList.check(timestamp, referredAssignment.getAssignmentType());
					isErroneous = ((ParameterisedSubReference) firstSubReference).checkParameters(timestamp,
							formalParameterList);
				} else {
					// if it is not a parameterless
					// template reference pointing
					// to a template having formal
					// parameters, where each has
					// default values
					if (!Assignment.Assignment_type.A_TEMPLATE.semanticallyEquals(referredAssignment.getAssignmentType())
							|| !formalParameterList.hasOnlyDefaultValues()) {
						final String message = MessageFormat.format(
								"Reference to parameterized definition `{0}'' without actual parameter list",
								referredAssignment.getIdentifier().getDisplayName());
						getLocation().reportSemanticError(message);
						setIsErroneous(true);
					}
				}
			}
		}

		return referredAssignment;
	}

	/**
	 * Returns the referenced declaration. Referenced by the given
	 * sub-reference. If the parameter is null, the module declaration will
	 * be returned.
	 *
	 * @param subReference
	 * @return The referenced declaration
	 */
	public Declaration getReferencedDeclaration(final ISubReference subReference) {
		final Assignment ass = getRefdAssignment(CompilationTimeStamp.getBaseTimestamp(), false);
		if (ass == null) {
			return null;
		}

		if (subReference == null) {
			return Declaration.createInstance(ass.getMyScope().getModuleScope());
		}

		if (subReferences.size() == 1 || subReferences.get(0) == subReference) {
			return Declaration.createInstance(ass);
		}

		final IType assignmentType = ass.getType(CompilationTimeStamp.getBaseTimestamp());
		if ( assignmentType == null ) {
			return null;
		}
		final IType type = assignmentType.getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());

		if (type instanceof IReferenceableElement) {
			final IReferenceableElement iTypeWithComponents = (IReferenceableElement) type;
			return iTypeWithComponents.resolveReference(this, 1, subReference);
		}

		return Declaration.createInstance(ass);
	}

	/**
	 * Checks and returns the type of the component referred to by this
	 * reference.
	 * <p>
	 * This is used to detect the type of "runs on" and "system" clause
	 * elements. In any other case a semantic error will be reported.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 *
	 * @return the type of the referred component or null in case of
	 *         problems.
	 * */
	public final Component_Type chkComponentypeReference(final CompilationTimeStamp timestamp) {
		final Assignment assignment = getRefdAssignment(timestamp, true);
		if (assignment != null) {
			if (Assignment_type.A_TYPE.semanticallyEquals(assignment.getAssignmentType())) {
				IType type = assignment.getType(timestamp);
				if (type != null) {
					type = type.getTypeRefdLast(timestamp);
					if (type != null && !type.getIsErroneous(timestamp)) {
						switch (type.getTypetype()) {
						case TYPE_COMPONENT:
							return (Component_Type) type;
						case TYPE_REFERENCED:
							return null;
						default:
							getLocation().reportSemanticError(COMPONENTEXPECTED);
							setIsErroneous(true);
							break;
						}
					}
				}
			} else {
				getLocation().reportSemanticError(TYPEEXPECTED);
				setIsErroneous(true);
			}
		}
		return null;
	}

	/**
	 * Checks and returns the type of the variable referred to by this
	 * reference.
	 * <p>
	 * This is used to detect the type of value redirects.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 *
	 * @return the type of the referred variable or null in case of
	 *         problems.
	 * */
	public IType checkVariableReference(final CompilationTimeStamp timestamp) {
		final Assignment assignment = getRefdAssignment(timestamp, true);
		if (assignment == null) {
			return null;
		}

		IType type;
		switch (assignment.getAssignmentType()) {
		case A_PAR_VAL_IN:
			((FormalParameter) assignment).useAsLValue(this);
			type = ((FormalParameter) assignment).getType(timestamp);
			((FormalParameter) assignment).setUsed();
			break;
		case A_PAR_VAL:
		case A_PAR_VAL_OUT:
		case A_PAR_VAL_INOUT:
			type = ((FormalParameter) assignment).getType(timestamp);
			((FormalParameter) assignment).setUsed();
			break;
		case A_VAR:
			type = ((Def_Var) assignment).getType(timestamp);
			((Def_Var) assignment).setUsed();
			break;
		default:
			getLocation().reportSemanticError(MessageFormat.format(VARIABLEXPECTED, assignment.getDescription()));
			setIsErroneous(true);
			return null;
		}

		final IType result = type.getFieldType(timestamp, this, 1, Expected_Value_type.EXPECTED_DYNAMIC_VALUE, null, false);
		if (result != null && subReferences != null && refersToStringElement()) {
			getLocation().reportSemanticError(MessageFormat.format(STRINGELEMENTUNUSABLE, result.getTypename()));
			setIsErroneous(true);
		}

		return result;
	}

	/**
	 * Checks whether this is a correct altstep activation reference.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 *
	 * @return true if the altstep reference is correct, false otherwise.
	 * */
	public final boolean checkActivateArgument(final CompilationTimeStamp timestamp) {
		final Assignment assignment = getRefdAssignment(timestamp, true);
		if (assignment == null) {
			return false;
		}

		if (!Assignment_type.A_ALTSTEP.semanticallyEquals(assignment.getAssignmentType())) {
			getLocation().reportSemanticError(MessageFormat.format(ALTSTEPEXPECTED, assignment.getDescription()));
			setIsErroneous(true);
			return false;
		}

		if (myScope != null) {
			myScope.checkRunsOnScope(timestamp, assignment, this, "activate");
		}

		if (!subReferences.isEmpty()) {
			if (Subreference_type.parameterisedSubReference.equals(subReferences.get(0).getReferenceType())) {
				final ActualParameterList actualParameters = ((ParameterisedSubReference) subReferences.get(0)).getActualParameters();
				final FormalParameterList formalParameterList = ((Def_Altstep) assignment).getFormalParameterList();

				if (formalParameterList != null) {
					return formalParameterList.checkActivateArgument(timestamp, actualParameters, assignment.getDescription());
				}
			}
		}

		return false;
	}

	/**
	 * Checks if the reference has unfoldable index sub-references.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 *
	 * @return true if the reference contains unfoldable index
	 *         sub-references, false otherwise.
	 * */
	public final boolean hasUnfoldableIndexSubReference(final CompilationTimeStamp timestamp) {
		for (final ISubReference subReference: subReferences) {
			if (Subreference_type.arraySubReference.equals(subReference.getReferenceType())) {
				IValue value = ((ArraySubReference) subReference).getValue();
				if (value != null) {
					value = value.setLoweridToReference(timestamp);
					if (value.isUnfoldable(timestamp)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Checks if the reference is actually refering to a setting of the
	 * provided type, or not.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param settingType
	 *                the setting type to check the reference against.
	 * @param referenceChain
	 *                a referencechain to detect cyclic references.
	 *
	 * @return true if the reference refers to a setting of the provided
	 *         kind, false otherwise.
	 * */
	public boolean refersToSettingType(final CompilationTimeStamp timestamp, final Setting_type settingType, final IReferenceChain referenceChain) {
		if (myScope == null) {
			return Setting_type.S_ERROR.equals(settingType);
		}

		final Assignment assignment = getRefdAssignment(timestamp, true);

		if (assignment == null) {
			return false;
		}

		if (!(assignment instanceof ASN1Assignment)) {
			getLocation().reportSemanticError(ASN1SETTINGEXPECTED);
			setIsErroneous(true);
			return false;
		}

		final ASN1Assignment asnAssignment = (ASN1Assignment) assignment;

		switch (settingType) {
		case S_OC:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_OC, referenceChain);
		case S_T:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_TYPE, referenceChain);
		case S_O:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_OBJECT, referenceChain);
		case S_V:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_CONST, referenceChain);
		case S_OS:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_OS, referenceChain);
		case S_VS:
			return asnAssignment.isAssignmentType(timestamp, Assignment_type.A_VS, referenceChain);
		case S_ERROR:
			return asnAssignment.getIsErroneous();
		default:
			// FATAL_ERROR
			return false;
		}
	}

	@Override
	/** {@inheritDoc} */
	public final String toString() {
		return getDisplayName();
	}

	public String getDisplayName() {
		final StringBuilder builder = new StringBuilder();
		if (modid != null) {
			builder.append(modid.getDisplayName());
		}
		for (int i = 0; i < subReferences.size(); i++) {
			subReferences.get(i).appendDisplayName(builder);
		}
		return builder.toString();
	}

	/**
	 * Handles the incremental parsing of this reference.
	 *
	 * @param reparser
	 *                the parser doing the incremental parsing.
	 * @param isDamaged
	 *                true if the location contains the damaged area, false
	 *                if only its' location needs to be updated.
	 * */
	@Override
	public final void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}

		if (modid != null) {
			reparser.updateLocation(modid.getLocation());
		}

		for (final ISubReference subreference : subReferences) {
			subreference.updateSyntax(reparser, false);
			reparser.updateLocation(subreference.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		for (int i = 0; i < subReferences.size(); i++) {
			subReferences.get(i).findReferences(referenceFinder, foundIdentifiers);
		}

		if (referredAssignment == null) {
			return;
		}

		if (referenceFinder.fieldId == null) {
			// we are searching for the assignment itself
			if (referenceFinder.assignment != referredAssignment) {
				return;
			}
			foundIdentifiers.add(new Hit(getId(), this));
		} else {
			// we are searching for a field of a type
			final IType t = referredAssignment.getType(CompilationTimeStamp.getBaseTimestamp());
			if (t == null) {
				return;
			}

			final List<IType> typeArray = new ArrayList<IType>();
			final boolean success = t.getFieldTypesAsArray(this, 1, typeArray);
			if (!success) {
				// TODO: maybe a partially erroneous reference could be searched too
				return;
			}
			//TODO: subReferences.size()>0 is just temporary. Rethink if it is correct or or not
			if (subReferences.size()>0 && subReferences.size() != typeArray.size() + 1) {
				ErrorReporter.INTERNAL_ERROR();
				return;
			}
			for (int i = 1; i < subReferences.size(); i++) {
				if (typeArray.get(i - 1) == referenceFinder.type && !(subReferences.get(i) instanceof ArraySubReference)
						&& subReferences.get(i).getId().equals(referenceFinder.fieldId)) {
					foundIdentifiers.add(new Hit(subReferences.get(i).getId()));
				}
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (modid != null && !modid.accept(v)) {
			return false;
		}

		if (subReferences != null) {
			for (int i = 0; i < subReferences.size(); i++) {
				if (!subReferences.get(i).accept(v)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public Declaration getDeclaration() {
		return getReferencedDeclaration(null);
	}

	/**
	 * Add generated java code on this level.
	 * @param aData only used to update imports if needed
	 * @param expression the expression for code generated
	 */
	public void generateCode( final JavaGenData aData, final ExpressionStruct expression ) {
		if (referredAssignment == null) {
			return;
		}

		boolean isTemplate;
		IType referedGovernor;
		switch (referredAssignment.getAssignmentType()) {
		case A_CONST:
		case A_EXT_CONST:
		case A_MODULEPAR:
		case A_VAR:
		case A_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RVAL:
		case A_PAR_VAL:
		case A_PAR_VAL_IN:
		case A_PAR_VAL_OUT:
		case A_PAR_VAL_INOUT:
			isTemplate = false;
			referedGovernor = referredAssignment.getType(CompilationTimeStamp.getBaseTimestamp());
			break;
		case A_MODULEPAR_TEMPLATE:
		case A_TEMPLATE:
		case A_VAR_TEMPLATE:
		case A_PAR_TEMP_IN:
		case A_PAR_TEMP_OUT:
		case A_PAR_TEMP_INOUT:
			isTemplate = true;
			referedGovernor = referredAssignment.getType(CompilationTimeStamp.getBaseTimestamp());
			break;
		default:
			isTemplate = false;
			referedGovernor = null;
			break;
		}

		FormalParameterList formalParameterList;
		switch (referredAssignment.getAssignmentType()) {
		case A_FUNCTION:
		case A_FUNCTION_RVAL:
		case A_FUNCTION_RTEMP:
			formalParameterList = ((Def_Function) referredAssignment).getFormalParameterList();
			break;
		case A_EXT_FUNCTION:
		case A_EXT_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RTEMP:
			formalParameterList = ((Def_Extfunction) referredAssignment).getFormalParameterList();
			break;
		case A_TEMPLATE:
			formalParameterList = ((Def_Template) referredAssignment).getFormalParameterList();
			break;
		case A_ALTSTEP:
			formalParameterList = ((Def_Altstep) referredAssignment).getFormalParameterList();
			break;
		default:
			formalParameterList = null;
			break;
		}

		if (subReferences.get(0) instanceof ParameterisedSubReference) {
			expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			expression.expression.append("( ");
			final ParameterisedSubReference temp = ((ParameterisedSubReference)subReferences.get(0));
			temp.getActualParameters().generateCodeAlias(aData, expression, formalParameterList);
			expression.expression.append(" )");
		} else if (formalParameterList != null) {
			//the reference does not have an actual parameter list, but the assignment has
			expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			expression.expression.append("( ");
			//FieldSubReference temp = ((FieldSubReference)subReferences.get(0));
			for (int i = 0; i < formalParameterList.getNofParameters(); i++) {
				if (i > 0){
					expression.expression.append(", ");
				}
				formalParameterList.getParameterByIndex(i).getDefaultValue().generateCode(aData, expression, formalParameterList.getParameterByIndex(i));
			}

			//temp.getActualParameters().generateCodeAlias(aData, expression);
			expression.expression.append(" )");
		} else {
			if (LazyFuzzyParamData.inLazyOrFuzzy()) {
				expression.expression.append(LazyFuzzyParamData.addReferenceGenname(aData, expression.expression, referredAssignment));
			} else {
				expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			}
		}

		if (referredAssignment.getMyScope() instanceof ComponentTypeBody) {
			switch (referredAssignment.getAssignmentType()) {
			case A_VAR:
			case A_VAR_TEMPLATE:
			case A_PORT:
			case A_TIMER:
				expression.expression.append(".get()");
				break;
			default:
				break;
			}
		}

		generateCode(aData, expression, subReferences, 1, isTemplate, false, referedGovernor);
	}

	/**
	 * originally has_single_expr
	 * */
	public boolean hasSingleExpression() {
		if (referredAssignment == null) {
			ErrorReporter.INTERNAL_ERROR("FATAL ERROR while generating code for reference `" + getFullName() + "''");
			return false;
		}

		for (int i = 0; i < subReferences.size(); i++) {
			final ISubReference temp = subReferences.get(i);
			if (!temp.hasSingleExpression(null)) {
				return false;
			}
		}

		FormalParameterList formalParameterList;
		switch (referredAssignment.getAssignmentType()) {
		case A_FUNCTION:
		case A_FUNCTION_RVAL:
		case A_FUNCTION_RTEMP:
			formalParameterList = ((Def_Function) referredAssignment).getFormalParameterList();
			break;
		case A_EXT_FUNCTION:
		case A_EXT_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RTEMP:
			formalParameterList = ((Def_Extfunction) referredAssignment).getFormalParameterList();
			break;
		case A_TEMPLATE:
			formalParameterList = ((Def_Template) referredAssignment).getFormalParameterList();
			break;
		case A_ALTSTEP:
			formalParameterList = ((Def_Altstep) referredAssignment).getFormalParameterList();
			break;
		default:
			formalParameterList = null;
			break;
		}

		if (formalParameterList != null) {
			if (subReferences.size() > 0) {
				if (!subReferences.get(0).hasSingleExpression(formalParameterList)) {
					return false;
				}
			}
			for (int i = 0; i < formalParameterList.getNofParameters(); i++) {
				if (formalParameterList.getParameterByIndex(i).getEvaluationType() != parameterEvaluationType.NORMAL_EVAL) {
					return false;
				}
			}
		}

		return true;
	}

	public void generateConstRef(final JavaGenData aData, final ExpressionStruct expression) {
		if ( referredAssignment == null ) {
			//TODO: handle null
			return;
		}

		boolean isTemplate;
		switch (referredAssignment.getAssignmentType()) {
		case A_CONST:
		case A_EXT_CONST:
		case A_MODULEPAR:
		case A_VAR:
		case A_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RVAL:
		case A_PAR_VAL:
		case A_PAR_VAL_IN:
		case A_PAR_VAL_OUT:
		case A_PAR_VAL_INOUT:
			isTemplate = false;
			break;
		case A_MODULEPAR_TEMPLATE:
		case A_TEMPLATE:
		case A_VAR_TEMPLATE:
		case A_PAR_TEMP_IN:
		case A_PAR_TEMP_OUT:
		case A_PAR_TEMP_INOUT:
			isTemplate = true;
			break;
		default:
			isTemplate = false;
			break;
		}

		FormalParameterList formalParameterList;
		switch (referredAssignment.getAssignmentType()) {
		case A_FUNCTION:
		case A_FUNCTION_RVAL:
		case A_FUNCTION_RTEMP:
			formalParameterList = ((Def_Function) referredAssignment).getFormalParameterList();
			break;
		case A_EXT_FUNCTION:
		case A_EXT_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RTEMP:
			formalParameterList = ((Def_Extfunction) referredAssignment).getFormalParameterList();
			break;
		case A_TEMPLATE:
			formalParameterList = ((Def_Template) referredAssignment).getFormalParameterList();
			break;
		case A_ALTSTEP:
			formalParameterList = ((Def_Altstep) referredAssignment).getFormalParameterList();
			break;
		default:
			formalParameterList = null;
			break;
		}

		final IType referedGovernor = referredAssignment.getType(CompilationTimeStamp.getBaseTimestamp());

		//ha parameterezett
		if (subReferences.get(0) instanceof ParameterisedSubReference) {
			expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			expression.expression.append("( ");
			final ParameterisedSubReference temp = ((ParameterisedSubReference)subReferences.get(0));
			final ActualParameterList actualParameterList = temp.getActualParameters();
			if (actualParameterList != null) {
				actualParameterList.generateCodeAlias(aData, expression, formalParameterList);
			}
			expression.expression.append(" )");
		} else if (formalParameterList != null) {
			//the reference does not have an actual parameter list, but the assignment has
			expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			expression.expression.append("( ");
			//FieldSubReference temp = ((FieldSubReference)subReferences.get(0));
			for (int i = 0; i < formalParameterList.getNofParameters(); i++) {
				if (i > 0){
					expression.expression.append(", ");
				}
				formalParameterList.getParameterByIndex(i).getDefaultValue().generateCode(aData, expression, formalParameterList.getParameterByIndex(i));
			}

			//temp.getActualParameters().generateCodeAlias(aData, expression);
			expression.expression.append(" )");
		} else {
			if (LazyFuzzyParamData.inLazyOrFuzzy()) {
				expression.expression.append(LazyFuzzyParamData.addReferenceGenname(aData, expression.expression, referredAssignment));
			} else {
				expression.expression.append(referredAssignment.getGenNameFromScope(aData, expression.expression, null));
			}
		}

		if (referredAssignment.getMyScope() instanceof ComponentTypeBody) {
			switch (referredAssignment.getAssignmentType()) {
			case A_VAR:
			case A_VAR_TEMPLATE:
			case A_PORT:
			case A_TIMER:
				expression.expression.append(".get()");
				break;
			default:
				break;
			}
		}

		generateCode(aData, expression, subReferences, 1, isTemplate, true, referedGovernor);
	}

	// originally fieldOrArrayRefs
	public static void generateCode(final JavaGenData aData, final ExpressionStruct expression, final ArrayList<ISubReference> subReferences, final int startIndex, final boolean isTemplate, final boolean isConst, IType type) {
		for ( int i = startIndex; i < subReferences.size(); i++) {
			if (type != null) {
				type = type.getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());
			}

			final ISubReference subreference = subReferences.get(i);
			if(Subreference_type.fieldSubReference.equals(subreference.getReferenceType())) {
				final Identifier id = ((FieldSubReference) subreference).getId();
				expression.expression.append(".");
				if (isConst) {
					expression.expression.append("constGet_field_");
				} else {
					expression.expression.append("get_field_");
				}
				expression.expression.append(FieldSubReference.getJavaGetterName(id.getName()));
				expression.expression.append("()");
				if (type != null) {
					CompField compField = null;
					switch(type.getTypetype()) {
					case TYPE_TTCN3_CHOICE:
					case TYPE_TTCN3_SEQUENCE:
					case TYPE_TTCN3_SET:
						compField = ((TTCN3_Set_Seq_Choice_BaseType)type).getComponentByName(id.getName());
						break;
					case TYPE_ANYTYPE:
						compField = ((Anytype_Type)type).getComponentByName(id.getName());
						break;
					case TYPE_OPENTYPE:
						compField = ((Open_Type)type).getComponentByName(id);
						break;
					case TYPE_ASN1_SEQUENCE:
						compField = ((ASN1_Sequence_Type)type).getComponentByName(id);
						break;
					case TYPE_ASN1_SET:
						compField = ((ASN1_Set_Type)type).getComponentByName(id);
						break;
					case TYPE_ASN1_CHOICE:
						compField = ((ASN1_Choice_Type)type).getComponentByName(id);
						break;
					default:
						ErrorReporter.INTERNAL_ERROR("FATAL ERROR while generating code for a reference.");
						return;
					}
					//TODO when can compfield == null  happen?
					if (i < subReferences.size() - 1 && compField != null && compField.isOptional() && !isTemplate) {
						if (isConst) {
							expression.expression.append(".constGet()");
						} else {
							expression.expression.append(".get()");
						}
					}

					if (compField == null) {
						ErrorReporter.INTERNAL_ERROR(MessageFormat.format("FATAL ERROR while generating code for a reference, type {0} does not have field with name {1}", type.getFullName(), id.getDisplayName()));
						return;
					}
					type = compField.getType();
				}
			} else if(Subreference_type.arraySubReference.equals(subreference.getReferenceType())) {
				final Value value = ((ArraySubReference)subreference).getValue();
				IType pt = value.getExpressionGovernor(CompilationTimeStamp.getBaseTimestamp(), Expected_Value_type.EXPECTED_TEMPLATE);
				//FIXME pt might be null?
				pt = pt.getTypeRefdLast(CompilationTimeStamp.getBaseTimestamp());
				if (pt.getTypetype() == Type_type.TYPE_ARRAY) {
					final long length = ((Array_Type)pt).getDimension().getSize();
					final long start = ((Array_Type)pt).getDimension().getOffset();
					// Generate the indexes as .constGet_at(x).constGet_at(y)...
					for (long j = start; j < start + length; j++) {
						if (isConst) {
							expression.expression.append(".constGet_at(");
						} else {
							expression.expression.append(".get_at(");
						}

						value.generateCodeExpressionMandatory(aData, expression, false);
						expression.expression.append(MessageFormat.format(".constGet_at({0}))", j));
					}
				} else if (pt.getTypetype() == Type_type.TYPE_SEQUENCE_OF) {
					final long length = ((SequenceOf_Type)pt).getSubtype().get_length_restriction();
					final long start = 0;
					// Generate the indexes as .constGet_at(x).constGet_at(y)...
					for (long j = start; j < start + length; j++) {
						if (isConst) {
							expression.expression.append(".constGet_at(");
						} else {
							expression.expression.append(".get_at(");
						}

						value.generateCodeExpressionMandatory(aData, expression, false);
						expression.expression.append(MessageFormat.format(".constGet_at({0}))", j));
					}
				} else {
					if (isConst) {
						expression.expression.append(".constGet_at(");
					} else {
						expression.expression.append(".get_at(");
					}

					value.generateCodeExpressionMandatory(aData, expression, false);
					expression.expression.append(")");
				}

				if(type != null) {
					switch(type.getTypetype()) {
					case TYPE_SEQUENCE_OF:
					case TYPE_SET_OF:
						type = ((AbstractOfType) type).getOfType();
						break;
					case TYPE_ARRAY:
						type = ((Array_Type) type).getElementType();
						break;
					default:
						type = null;
					}
				}
			}
		}
	}

	/**
	 * Generates code for checking if the reference
	 * and the referred objects are bound or not.
	 *
	 * generate_code_ispresentbound in the compiler
	 *
	 * @param aData only used to update imports if needed
	 * @param expression the expression for code generated
	 * @param isTemplate if the reference is pointing to a template
	 * @param optype true to generate code for isbound, false otherwise
	 * @param field the expression to be used to reach the last union's field, or null.
	 * */
	public void generateCodeIsPresentBoundChosen(final JavaGenData aData, final ExpressionStruct expression, final boolean isTemplate,
			final Operation_type optype, final String field) {
		final Assignment assignment = getRefdAssignment(CompilationTimeStamp.getBaseTimestamp(), false);
		final String ass_id = assignment.getGenNameFromScope(aData, expression.expression, null);

		String ass_id2 = ass_id;
		FormalParameterList formalParameterList;
		switch (assignment.getAssignmentType()) {
		case A_FUNCTION:
		case A_FUNCTION_RVAL:
		case A_FUNCTION_RTEMP:
			formalParameterList = ((Def_Function) assignment).getFormalParameterList();
			break;
		case A_EXT_FUNCTION:
		case A_EXT_FUNCTION_RVAL:
		case A_EXT_FUNCTION_RTEMP:
			formalParameterList = ((Def_Extfunction) assignment).getFormalParameterList();
			break;
		case A_TEMPLATE:
			formalParameterList = ((Def_Template) assignment).getFormalParameterList();
			break;
		default:
			formalParameterList = null;
			break;
		}
		if (subReferences.size() > 0 && subReferences.get(0) instanceof ParameterisedSubReference) {
			final ParameterisedSubReference subReference = (ParameterisedSubReference) subReferences.get(0);
			final ExpressionStruct tempExpression = new ExpressionStruct();
			subReference.getActualParameters().generateCodeAlias(aData, tempExpression, formalParameterList);

			if(tempExpression.preamble.length() > 0) {
				expression.preamble.append(tempExpression.preamble);
			}
			ass_id2 = MessageFormat.format("{0}({1})", ass_id, tempExpression.expression);
		} else if (formalParameterList != null) {
			// the reference does not have an actual parameter list,
			// but the assignment has
			final ExpressionStruct tempExpression = new ExpressionStruct();
			final StringBuilder newId = new StringBuilder();
			newId.append(assignment.getGenNameFromScope(aData, tempExpression.expression, null));
			newId.append("( ");

			for (int i = 0; i < formalParameterList.getNofParameters(); i++) {
				if (i > 0) {
					tempExpression.expression.append(", ");
				}
				formalParameterList.getParameterByIndex(i).getDefaultValue().generateCode(aData, tempExpression, formalParameterList.getParameterByIndex(i));
			}

			if(tempExpression.preamble.length() > 0) {
				expression.preamble.append(tempExpression.preamble);
			}
			newId.append(tempExpression.expression);
			newId.append(" )");
			ass_id2 = newId.toString();
		}

		if (referredAssignment.getMyScope() instanceof ComponentTypeBody) {
			switch (referredAssignment.getAssignmentType()) {
			case A_VAR:
			case A_VAR_TEMPLATE:
			case A_PORT:
			case A_TIMER:
				ass_id2 = ass_id2 + ".get()";
				break;
			default:
				break;
			}
		}

		if (subReferences.size() > 1) {
			final String tempGeneralId = aData.getTemporaryVariableName();
			final ExpressionStruct isboundExpression = new ExpressionStruct();

			isboundExpression.preamble.append(MessageFormat.format("boolean {0} = {1}.is_bound();\n", tempGeneralId, ass_id2));

			final IType type = assignment.getType(CompilationTimeStamp.getBaseTimestamp());
			type.generateCodeIsPresentBoundChosen(aData, isboundExpression, subReferences, 1, tempGeneralId, ass_id2, isTemplate, optype, field, getMyScope());

			expression.preamble.append(isboundExpression.preamble);
			expression.preamble.append(isboundExpression.expression);
			expression.expression.append(tempGeneralId);
		} else {
			switch (optype) {
			case ISBOUND_OPERATION:
				expression.expression.append(MessageFormat.format("{0}.is_bound()", ass_id2));
				break;
			case ISVALUE_OPERATION:
				expression.expression.append(MessageFormat.format("{0}.is_value()", ass_id2));
				break;
			case ISPRESENT_OPERATION:
				expression.expression.append(MessageFormat.format("{0}.is_present({1})", ass_id2, isTemplate && aData.getAllowOmitInValueList()? "true":""));
				break;
			case ISCHOOSEN_OPERATION:
				expression.expression.append(MessageFormat.format("{0}.ischosen({1})", ass_id2, field));
				break;
			default:
				ErrorReporter.INTERNAL_ERROR("FATAL ERROR while generating code for reference `" + getFullName() + "''");
				break;
			}

		}
	}
}
