/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.editors.configeditor.actions;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.common.parsers.Interval;
import org.eclipse.titan.common.parsers.cfg.CfgDefinitionInformation;
import org.eclipse.titan.common.parsers.cfg.CfgInterval;
import org.eclipse.titan.common.parsers.cfg.CfgInterval.section_type;
import org.eclipse.titan.common.parsers.cfg.CfgLocation;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.Assignments;
import org.eclipse.titan.designer.AST.Location;
import org.eclipse.titan.designer.AST.Module;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.consoles.TITANDebugConsole;
import org.eclipse.titan.designer.editors.GlobalIntervalHandler;
import org.eclipse.titan.designer.editors.actions.OpenDeclarationBase;
import org.eclipse.titan.designer.editors.configeditor.ConfigEditor;
import org.eclipse.titan.designer.editors.configeditor.ConfigReferenceParser;
import org.eclipse.titan.designer.editors.configeditor.ConfigTextEditor;
import org.eclipse.titan.designer.parsers.GlobalParser;
import org.eclipse.titan.designer.parsers.ProjectSourceParser;
import org.eclipse.titan.designer.preferences.PreferenceConstants;
import org.eclipse.titan.designer.productUtilities.ProductConstants;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;

/**
 * @author Ferenc Kovacs
 * @author Adam Knapp
 * */
public final class OpenDeclaration extends OpenDeclarationBase {
	public static final String NOTDEFINITION = "Selected text does not resolve to a definition";
	public static final String NOTMODULEPARDECLARATION = "Selected text does not resolve to a module parameter declaration";
	public static final String NOTCONSTANTDEFINITION = "Current text selection does not resolve to a constant definition";
	public static final String WRONGSELECTION = "Selected text cannot be mapped to a file name";
	public static final String CONFIGEDITOR = "configuration file";
	public static final String FILENOTFOUND = "Could not find included configuration file `{0}'' on include paths";

	/**
	 * Opens an editor for the provided declaration, and in this editor the
	 * location of the declaration is revealed and highlighted.
	 *
	 * @param file
	 *                The file to open.
	 * @param offset
	 *                The start position of the declaration to select.
	 * @param endOffset
	 *                The end position of the declaration to select.
	 * @param select
	 *                Select the given region if true.
	 */
	private void selectAndRevealRegion(final IFile file, final int offset, final int endOffset, final boolean select) {
		final IEditorDescriptor desc = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(file.getName());
		if (desc == null) {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(MessageFormat.format(EDITORNOTFOUND, CONFIGEDITOR));
			return;
		}

		if (!select) {
			return;
		}

		try {
			final IWorkbenchPage page = targetEditor.getSite().getPage();
			final IEditorPart editorPart = page.openEditor(new FileEditorInput(file), desc.getId());

			if (editorPart != null) {
				// Check the editor instance. It's usually a
				// ConfigEditor and
				// not AbstractTextEditor.
				if (editorPart instanceof ConfigEditor) {
					((AbstractTextEditor) ((ConfigEditor) editorPart).getEditor()).selectAndReveal(offset, endOffset - offset);
				} else if (editorPart instanceof AbstractTextEditor) {
					((AbstractTextEditor) editorPart).selectAndReveal(offset, endOffset - offset);
				}
			}
		} catch (PartInitException e) {
			ErrorReporter.logExceptionStackTrace(e);
		}
	}

	/**
	 * Display a list of items in a dialog and return the selected item as
	 * an object.
	 *
	 * @param collected
	 *                The list of items to be displayed in the dialog.
	 * @return The object selected from the dialog.
	 */
	public Object openCollectionListDialog(final List<?> collected) {
		final OpenDeclarationLabelProvider labelProvider = new OpenDeclarationLabelProvider();
		final ElementListSelectionDialog dialog = new ElementListSelectionDialog(null, labelProvider);
		dialog.setTitle("Open");
		dialog.setMessage("Select the element to open");
		dialog.setElements(collected.toArray());
		if (dialog.open() == Window.OK) {
			return dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * Display a list of assignments in a dialog and return the selected assignment.
	 *
	 * @param collected
	 *                The list of assignments to be displayed in the dialog.
	 * @return The assignment selected from the dialog.
	 */
	private Assignment openCollectionListDialog(final ArrayList<Assignment> assignments) {
		final OpenDeclarationLabelProvider labelProvider = new OpenDeclarationLabelProvider();
		final ElementListSelectionDialog dialog = new ElementListSelectionDialog(null, labelProvider);
		dialog.setTitle("Open");
		dialog.setMessage("Select the element to open");
		dialog.setElements(assignments.toArray());
		if (dialog.open() == Window.OK) {
			return (Assignment) dialog.getFirstResult();
		}
		return null;
	}

	/**
	 * Provides position dependent section information.
	 *
	 * @param document
	 *                The document where the check takes place.
	 * @param offset
	 *                The current position of the cursor.
	 * @return The type of the section for the current position of the
	 *         cursor.
	 */
	public section_type getSection(final IDocument document, final int offset) {
		final Interval interval = GlobalIntervalHandler.getInterval(document);
		if (interval == null) {
			return section_type.UNKNOWN;
		}

		for (final Interval subInterval : interval.getSubIntervals()) {
			final int startOffset = subInterval.getStartOffset();
			final int endOffset = subInterval.getEndOffset();
			if (subInterval instanceof CfgInterval && startOffset <= offset && endOffset >= offset) {
				return ((CfgInterval) subInterval).getSectionType();
			}
		}

		return section_type.UNKNOWN;
	}

	/**
	 * Opens the included configuration file selected by the user.
	 *
	 * @param file
	 *                The current file.
	 * @param offset
	 *                The position of the cursor.
	 * @param document
	 *                The document opened in the configuration file editor.
	 */
	public void handleIncludes(final IFile file, final int offset, final IDocument document) {
		final ConfigReferenceParser refParser = new ConfigReferenceParser(false);
		final String include = refParser.findIncludedFileForOpening(offset, document);

		if (include == null || include.length() == 0) {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(WRONGSELECTION);
			return;
		}

		final IFile fileToOpen = file.getProject().getFile(include);
		if (fileToOpen == null || !fileToOpen.exists()) {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager()
			.setErrorMessage(MessageFormat.format(FILENOTFOUND, include));
			return;
		}

		selectAndRevealRegion(fileToOpen, -1, -1, false);
	}

	/**
	 * Selects and reveals the selected module parameter.
	 *
	 * @param file
	 *                The current file.
	 * @param offset
	 *                The position of the cursor.
	 * @param document
	 *                The document opened in the configuration file editor.
	 * @return True
	 */
	public boolean handleModuleParameters(final IFile file, final int offset, final IDocument document) {
		final ConfigReferenceParser refParser = new ConfigReferenceParser(false);
		final Reference reference = refParser.findReferenceForOpening(file, offset, document);
		if (refParser.isModuleParameter()) {
			if (reference == null) {
				return false;
			}

			final ProjectSourceParser projectSourceParser = GlobalParser.getProjectSourceParser(file.getProject());
			final String exactModuleName = refParser.getExactModuleName();
			final ArrayList<Assignment> foundAssignments = new ArrayList<Assignment>();

			if (exactModuleName != null) {
				final Module module = projectSourceParser.getModuleByName(exactModuleName);
				if (module != null) {
					final Assignments assignments = module.getAssignments();
					for (int i = 0; i < assignments.getNofAssignments(); i++) {
						final Assignment assignment = assignments.getAssignmentByIndex(i);
						if (assignment.getIdentifier().getDisplayName().equals(reference.getId().getDisplayName())) {
							foundAssignments.add(assignment);
						}
					}
				}
			} else {
				for (final String moduleName : projectSourceParser.getKnownModuleNames()) {
					final Module module = projectSourceParser.getModuleByName(moduleName);
					if (module != null) {
						final Assignments assignments = module.getAssignments();
						for (int i = 0; i < assignments.getNofAssignments(); i++) {
							final Assignment assignment = assignments.getAssignmentByIndex(i);
							if (assignment.getIdentifier().getDisplayName().equals(reference.getId().getDisplayName())) {
								foundAssignments.add(assignment);
							}
						}
					}
				}
			}

			if (foundAssignments.isEmpty()) {
				targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(NOTMODULEPARDECLARATION);
				return false;
			}

			//			List<DeclarationCollectionHelper> collected = declarationCollector.getCollected();
			//			DeclarationCollectionHelper declaration = null;
			Assignment assignment = null;

			if (foundAssignments.size() == 1) {
				assignment = foundAssignments.get(0);
			} else {
				final Assignment result = openCollectionListDialog(foundAssignments);
				if (result != null) {
					assignment = result;
				}
			}
			final IPreferencesService prefs = Platform.getPreferencesService();
			if (prefs.getBoolean(ProductConstants.PRODUCT_ID_DESIGNER, PreferenceConstants.DISPLAYDEBUGINFORMATION, true, null)) {
				for (final Assignment tempAssignment : foundAssignments) {
					final Location location = tempAssignment.getLocation();
					TITANDebugConsole.println("Module parameter: " + location.getFile() + ":"
							+ location.getOffset() + "-"
							+ location.getEndOffset());
				}
			}

			if (assignment != null) {
				final Location location = assignment.getLocation();
				selectAndRevealRegion((IFile) location.getFile(), location.getOffset(), location.getEndOffset(), true);
			}

			return true;
		}

		return false;
	}

	/**
	 * Jumps to the definition of the constant selected by the user.
	 *
	 * @param file
	 *                The current file.
	 * @param offset
	 *                The position of the cursor.
	 * @param document
	 *                The document opened in the configuration file editor.
	 */
	public void handleDefinitions(final IFile file, final int offset, final IDocument document) {
		final ConfigReferenceParser refParser = new ConfigReferenceParser(false);
		refParser.findReferenceForOpening(file, offset, document);
		final String definitionName = refParser.getDefinitionName();

		if (definitionName == null) {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(NOTDEFINITION);
			return;
		}

		final List<ConfigDeclarationCollectionHelper> collected = new ArrayList<ConfigDeclarationCollectionHelper>();
		final Map<String, CfgDefinitionInformation> definitions = GlobalParser.getConfigSourceParser(file.getProject()).getAllDefinitions();
		final CfgDefinitionInformation definition = definitions.get(definitionName);

		if (definition != null) {
			final List<CfgLocation> locations = definition.getLocations();
			for (final CfgLocation location : locations) {
				collected.add(new ConfigDeclarationCollectionHelper(definitionName, location));
			}
		} else {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(NOTCONSTANTDEFINITION);
			return;
		}

		if (collected.isEmpty()) {
			targetEditor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(NOTCONSTANTDEFINITION);
			return;
		}

		ConfigDeclarationCollectionHelper declaration = null;
		if (collected.size() == 1) {
			declaration = collected.get(0);
		} else {
			final Object result = openCollectionListDialog(collected);
			if (result != null) {
				declaration = (ConfigDeclarationCollectionHelper) result;
			}
		}

		if (declaration != null) {
			selectAndRevealRegion(declaration.location.getFile(), declaration.location.getOffset(), declaration.location.getEndOffset(),
					true);
		}
	}
	
	@Override
	/** {@inheritDoc} */
	protected final void doOpenDeclaration() {
		if (targetEditor instanceof ConfigEditor) {
			targetEditor = ((ConfigEditor) targetEditor).getEditor();
		}

		if (targetEditor == null || !(targetEditor instanceof ConfigTextEditor)) {
			return;
		}

		if (!check()) {
			return;
		}

		final IFile file = (IFile) targetEditor.getEditorInput().getAdapter(IFile.class);
		int offset;
		if (!selection.isEmpty() && selection instanceof TextSelection && !"".equals(((TextSelection) selection).getText())) {
			final IPreferencesService prefs = Platform.getPreferencesService();
			if (prefs.getBoolean(ProductConstants.PRODUCT_ID_DESIGNER, PreferenceConstants.DISPLAYDEBUGINFORMATION, true, null)) {
				TITANDebugConsole.println("Selected: " + ((TextSelection) selection).getText());
			}
			final TextSelection textSelection = (TextSelection) selection;
			offset = textSelection.getOffset() + textSelection.getLength();
		} else {
			offset = ((ConfigTextEditor) targetEditor).getCarretOffset();
		}

		final IDocument document = ((ConfigTextEditor) targetEditor).getDocument();
		final section_type section = getSection(document, offset);

		if (section_type.UNKNOWN.equals(section)) {
			if (handleModuleParameters(file, offset, document)) {
				return;
			}
			return;
		} else if (section_type.INCLUDE.equals(section)) {
			handleIncludes(file, offset, document);
			return;
		} else if (section_type.MODULE_PARAMETERS.equals(section)) {
			// Module parameters are always defined in [MODULE_PARAMETERS] section. 
			// Don't go further if the selected text can be identifiable as a module parameter.
			if (handleModuleParameters(file, offset, document)) {
				return;
			}
		}

		// Fall back.
		handleDefinitions(file, offset, document);
	}
}
