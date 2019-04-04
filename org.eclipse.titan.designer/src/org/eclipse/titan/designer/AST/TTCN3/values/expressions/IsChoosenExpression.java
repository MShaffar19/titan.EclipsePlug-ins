/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.values.expressions;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.BridgingNamedNode;
import org.eclipse.titan.designer.AST.FieldSubReference;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.ISubReference.Subreference_type;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IType.Type_type;
import org.eclipse.titan.designer.AST.IValue;
import org.eclipse.titan.designer.AST.Identifier;
import org.eclipse.titan.designer.AST.Module;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Choice_Type;
import org.eclipse.titan.designer.AST.ASN1.types.Open_Type;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.templates.Referenced_Template;
import org.eclipse.titan.designer.AST.TTCN3.templates.TTCN3Template;
import org.eclipse.titan.designer.AST.TTCN3.types.Anytype_Type;
import org.eclipse.titan.designer.AST.TTCN3.types.CompField;
import org.eclipse.titan.designer.AST.TTCN3.types.TTCN3_Choice_Type;
import org.eclipse.titan.designer.AST.TTCN3.values.Anytype_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Boolean_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Choice_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.Referenced_Value;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * @author Kristof Szabados
 * */
public final class IsChoosenExpression extends Expression_Value {
	private static final String CONSTANTREFERENCEEXPECTED = "Reference to a constant value was expected instead of {0}";
	private static final String STATICREFERENCEEXPECTED = "Reference to a static value was expected instead of {0}";
	private static final String VALUETEMPLATEEXPECTED = "Reference to a value or template was expected instead of {0}";
	private static final String OPERANDERROR = "The operand of operation `ischosen()'' should be a record or set value or template, instead of `{0}''";
	private static final String MISSINGFIELD = "Type `{0}'' does not have a field named `{1}''";

	private final Reference reference;

	/** temporal value generated by the operand check. */
	private Identifier identifier;

	/** cache, used only when the reference is pointing to a value */
	private Referenced_Value value;
	/** cache, used only when the reference is pointing to a template */
	private Referenced_Template template;

	private CompilationTimeStamp lastTimeoperandsChecked;

	public IsChoosenExpression(final Reference reference) {
		this.reference = reference;

		if (reference != null) {
			reference.setFullNameParent(this);
		}
	}

	@Override
	/** {@inheritDoc} */
	public Operation_type getOperationType() {
		return Operation_type.ISCHOOSEN_OPERATION;
	}

	@Override
	/** {@inheritDoc} */
	public boolean checkExpressionSelfReference(final CompilationTimeStamp timestamp, final Assignment lhs) {
		if (template != null && template.checkExpressionSelfReferenceTemplate(timestamp, lhs)) {
			return true;
		}
		if (value != null && value.checkExpressionSelfReferenceValue(timestamp, lhs)) {
			return true;
		}

		return false;
	}

	@Override
	/** {@inheritDoc} */
	public String createStringRepresentation() {
		final StringBuilder builder = new StringBuilder("ischoosen(");
		builder.append(reference.getDisplayName());
		builder.append(')');
		return builder.toString();
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);

		if (reference != null) {
			reference.setMyScope(scope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void setCodeSection(final CodeSectionType codeSection) {
		super.setCodeSection(codeSection);

		if (reference != null) {
			reference.setCodeSection(codeSection);
		}
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (reference == child) {
			return builder.append(OPERAND);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public Type_type getExpressionReturntype(final CompilationTimeStamp timestamp, final Expected_Value_type expectedValue) {
		return Type_type.TYPE_BOOL;
	}

	@Override
	/** {@inheritDoc} */
	public boolean isUnfoldable(final CompilationTimeStamp timestamp, final Expected_Value_type expectedValue,
			final IReferenceChain referenceChain) {
		checkExpressionOperands(timestamp, expectedValue, referenceChain);
		if (value == null || getIsErroneous(timestamp)) {
			return true;
		}

		return value.isUnfoldable(timestamp, expectedValue, referenceChain);
	}

	/**
	 * Checks the parameters of the expression and if they are valid in
	 * their position in the expression or not.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param expectedValue
	 *                the kind of value expected.
	 * @param referenceChain
	 *                a reference chain to detect cyclic references.
	 * */
	private void checkExpressionOperands(final CompilationTimeStamp timestamp, final Expected_Value_type expectedValue,
			final IReferenceChain referenceChain) {
		if (lastTimeoperandsChecked != null && !lastTimeoperandsChecked.isLess(timestamp)) {
			return;
		}

		lastTimeoperandsChecked = timestamp;
		value = null;
		identifier = null;

		if (reference == null || reference.getSubreferences().size() < 2) {
			setIsErroneous(true);
			return;
		}

		final Reference tempReference = reference.newInstance();
		tempReference.setFullNameParent(this);
		tempReference.setMyScope(getMyScope());
		final ISubReference subreference = tempReference.removeLastSubReference();
		if (Subreference_type.fieldSubReference.equals(subreference.getReferenceType())) {
			identifier = ((FieldSubReference) subreference).getId();
		} else {
			setIsErroneous(true);
			return;
		}

		final Assignment assignment = tempReference.getRefdAssignment(timestamp, true);
		if (assignment == null) {
			setIsErroneous(true);
			return;
		}

		IType governor;
		switch (assignment.getAssignmentType()) {
		case A_CONST:
		case A_EXT_CONST:
		case A_MODULEPAR:
		case A_VAR:
		case A_PAR_VAL:
		case A_PAR_VAL_IN:
		case A_PAR_VAL_OUT:
		case A_PAR_VAL_INOUT: {
			value = new Referenced_Value(tempReference);
			value.setLocation(tempReference.getLocation());
			value.setMyScope(getMyScope());

			final BridgingNamedNode tempNamedNode = new BridgingNamedNode(this, OPERAND);
			value.setFullNameParent(tempNamedNode);

			governor = value.getExpressionGovernor(timestamp, expectedValue);
			if (governor == null) {
				setIsErroneous(true);
			} else {
				value.setMyGovernor(governor);
				final IValue tempValue2 = governor.checkThisValueRef(timestamp, value);
				if (tempValue2.getIsErroneous(timestamp)) {
					setIsErroneous(true);
				}
			}
			break;
		}
		case A_TEMPLATE:
		case A_VAR_TEMPLATE:
		case A_PAR_TEMP_IN:
		case A_PAR_TEMP_OUT:
		case A_PAR_TEMP_INOUT: {
			template = new Referenced_Template(tempReference);
			template.setLocation(tempReference.getLocation());
			template.setMyScope(getMyScope());

			final BridgingNamedNode tempNamedNode = new BridgingNamedNode(this, OPERAND);
			template.setFullNameParent(tempNamedNode);

			if (Expected_Value_type.EXPECTED_DYNAMIC_VALUE.equals(expectedValue)
					|| Expected_Value_type.EXPECTED_DYNAMIC_VALUE.equals(expectedValue)) {
				governor = template.getExpressionGovernor(timestamp, Expected_Value_type.EXPECTED_TEMPLATE);
			} else {
				governor = template.getExpressionGovernor(timestamp, expectedValue);
			}

			if (governor == null) {
				setIsErroneous(true);
			} else {
				template.setMyGovernor(governor);
				final TTCN3Template last = template.getTemplateReferencedLast(timestamp, referenceChain);
				if (last.getIsErroneous(timestamp)) {
					setIsErroneous(true);
				}
			}

			if (!Expected_Value_type.EXPECTED_TEMPLATE.equals(expectedValue)
					&& !Expected_Value_type.EXPECTED_DYNAMIC_VALUE.equals(expectedValue)) {
				if (Expected_Value_type.EXPECTED_CONSTANT.equals(expectedValue)) {
					template.getLocation().reportSemanticError(
							MessageFormat.format(CONSTANTREFERENCEEXPECTED, assignment.getDescription()));
				} else {
					template.getLocation().reportSemanticError(
							MessageFormat.format(STATICREFERENCEEXPECTED, assignment.getDescription()));
				}

				setIsErroneous(true);
			}
			break;
		}
		default:
			tempReference.getLocation().reportSemanticError(MessageFormat.format(VALUETEMPLATEEXPECTED, assignment.getDescription()));
			setIsErroneous(true);
			return;
		}

		if (governor != null) {
			governor = governor.getTypeRefdLast(timestamp);
			if (!governor.getIsErroneous(timestamp)) {
				CompField field = null;
				switch (governor.getTypetype()) {
				case TYPE_ASN1_CHOICE:
					if (((ASN1_Choice_Type) governor).hasComponentWithName(identifier)) {
						field = ((ASN1_Choice_Type) governor).getComponentByName(identifier);
					}
					break;
				case TYPE_TTCN3_CHOICE:
					if (((TTCN3_Choice_Type) governor).hasComponentWithName(identifier.getName())) {
						field = ((TTCN3_Choice_Type) governor).getComponentByName(identifier.getName());
					}
					break;
				case TYPE_OPENTYPE:
					if (((Open_Type) governor).hasComponentWithName(identifier)) {
						field = ((Open_Type) governor).getComponentByName(identifier);
					}
					break;
				case TYPE_ANYTYPE:
					if (((Anytype_Type) governor).hasComponentWithName(identifier.getName())) {
						field = ((Anytype_Type) governor).getComponentByName(identifier.getName());
					}
					break;
				default:
					location.reportSemanticError(MessageFormat.format(OPERANDERROR, governor.getTypename()));
					setIsErroneous(true);
					return;
				}

				if (null == field) {
					location.reportSemanticError(MessageFormat.format(MISSINGFIELD, governor.getTypename(),
							identifier.getDisplayName()));
					setIsErroneous(true);
				}
			}
		}
	}

	@Override
	/** {@inheritDoc} */
	public IValue evaluateValue(final CompilationTimeStamp timestamp, final Expected_Value_type expectedValue,
			final IReferenceChain referenceChain) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return lastValue;
		}

		isErroneous = false;
		lastTimeChecked = timestamp;
		lastValue = this;

		if (reference == null) {
			return lastValue;
		}

		checkExpressionOperands(timestamp, expectedValue, referenceChain);

		if (getIsErroneous(timestamp)) {
			return lastValue;
		}

		if (value == null) {
			return lastValue;
		}

		if (isUnfoldable(timestamp, referenceChain)) {
			return lastValue;
		}

		IValue last = value.getValueRefdLast(timestamp, referenceChain);
		boolean result;
		switch (last.getValuetype()) {
		case CHOICE_VALUE:
			result = ((Choice_Value) last).fieldIsChosen(identifier);
			break;
		case SEQUENCE_VALUE:
			last = last.setValuetype(timestamp, Value_type.CHOICE_VALUE);
			result = ((Choice_Value) last).fieldIsChosen(identifier);
			break;
		case ANYTYPE_VALUE:
			result = ((Anytype_Value) last).fieldIsChosen(timestamp, identifier);
			break;
		default:
			setIsErroneous(true);
			return last;
		}

		lastValue = new Boolean_Value(result);
		lastValue.copyGeneralProperties(this);

		return lastValue;
	}

	@Override
	/** {@inheritDoc} */
	public void checkRecursions(final CompilationTimeStamp timestamp, final IReferenceChain referenceChain) {
		checkExpressionOperands(timestamp, Expected_Value_type.EXPECTED_DYNAMIC_VALUE, referenceChain);
		if (referenceChain.add(this)) {
			referenceChain.markState();
			if (value != null) {
				value.checkRecursions(timestamp, referenceChain);
			}
			if (template != null) {
				template.checkRecursions(timestamp, referenceChain);
			}
			referenceChain.previousState();
		}
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			throw new ReParseException();
		}

		if (reference != null) {
			reference.updateSyntax(reparser, false);
			reparser.updateLocation(reference.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		if (reference == null) {
			return;
		}

		reference.findReferences(referenceFinder, foundIdentifiers);
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (reference != null && !reference.accept(v)) {
			return false;
		}

		return true;
	}

	@Override
	/** {@inheritDoc} */
	public void reArrangeInitCode(final JavaGenData aData, final StringBuilder source, final Module usageModule) {
		if (value != null) {
			value.reArrangeInitCode(aData, source, usageModule);
		}
		if (template != null) {
			template.reArrangeInitCode(aData, source, usageModule);
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean canGenerateSingleExpression() {
		return false;
	}

	@Override
	/** {@inheritDoc} */
	public boolean returnsNative() {
		return true;
	}


	@Override
	/** {@inheritDoc} */
	public void generateCodeExpressionExpression(final JavaGenData aData, final ExpressionStruct expression) {
		if (value != null) {
			final Reference reference = value.getReference();
			if (reference != null) {
				final String genNameValue = value.getMyGovernor().getGenNameValue(aData, expression.expression);
				final String field = MessageFormat.format("{0}.union_selection_type.ALT_{1}", genNameValue, FieldSubReference.getJavaGetterName(identifier.getName()));
				reference.generateCodeIsPresentBoundChosen(aData, expression, false, getOperationType(), field);
			}
		}
		if (template != null) {
			final Reference reference = template.getReference();
			if (reference != null) {
				final String genNameValue = template.getMyGovernor().getGenNameValue(aData, expression.expression);
				final String field = MessageFormat.format("{0}.union_selection_type.ALT_{1}", genNameValue, FieldSubReference.getJavaGetterName(identifier.getName()));
				reference.generateCodeIsPresentBoundChosen(aData, expression, true, getOperationType(), field);
			}
		}
	}
}
