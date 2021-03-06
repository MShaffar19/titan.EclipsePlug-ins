/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.samples;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.common.utils.FileUtils;

/**
 * @author Szabolcs Beres
 * @author Adam Knapp
 * */
public abstract class SampleProject {
	/**
	 * Creates the files of the project.
	 * @see SampleProject#getSourceFileContent()
	 * @see SampleProject#getOtherFileContent()
	 * @param project The target project.
	 */
	public void setupProject(final IProject project, final IFolder sourceFolder) {
		try {
			if (!sourceFolder.exists()) {
				sourceFolder.create(true, true, new NullProgressMonitor());
			}

			preconfigure(project);

			setupFiles(getSourceFileContent(), sourceFolder);
			setupFiles(getOtherFileContent(), project);

			configure(project);

		} catch (CoreException e) {
			ErrorReporter.logExceptionStackTrace("Error while creating project", e);
		}
	}

	/**
	 * Preconfigures the project and/or prepares the source codes before the files are created.
	 * @param project the project to configure
	 */
	protected void preconfigure(final IProject project) {}

	/**
	 * Configures the project after the files have been created.
	 * @param project the project to configure
	 */
	protected abstract void configure(final IProject project);

	/**
	 * Creates the files with the given content
	 * @param files The map of the files and their content.
	 * @param root The target directory.
	 * @throws CoreException when something goes wrong ...
	 */
	private void setupFiles(final Map<String, String> files, final IContainer root) throws CoreException {
		for (final Map.Entry<String, String> entry : files.entrySet()) {
			final int indexOfLastSlash = entry.getKey().lastIndexOf('/');
			final String filename = entry.getKey().substring(indexOfLastSlash + 1);

			IFile file;
			if (indexOfLastSlash != -1) {
				final String dir = entry.getKey().substring(0, indexOfLastSlash);
				final IFolder folder = root.getFolder(new Path(dir));
				if (!folder.exists()) {
					createFolder(folder);
				}
				file = folder.getFile(filename);
			} else {
				file = root.getFile(new Path(entry.getKey()));
			}

			file.create(new BufferedInputStream(new ByteArrayInputStream(entry.getValue().getBytes())), true, new NullProgressMonitor());
		}
	}

	/**
	 * Creates the given folder.
	 * @param folder
	 * @throws CoreException
	 */
	private void createFolder(final IFolder folder) throws CoreException {
		FileUtils.createDir(folder);
	}

	/**
	 * @return the name of the project
	 */
	public abstract String getName();

	/**
	 * @return the description of the project
	 */
	public abstract String getDescription();

	/**
	 * Returns the content of the source files, where the key is the filename and the value is the content of the file.
	 * The filenames should contain the relative path from the source folder to the file.
	 * e.g. dir1/.../dir2/filename => The directory structure sourceFolder/dir1/.../dir2 will be created,
	 * where the directory dir2 will contain the file.
	 * @return The file content
	 */
	public abstract Map<String, String> getSourceFileContent();

	/**
	 * Returns the content of the files, which are not source files. The key is the filename and the value is the content of the file.
	 * The filenames should contain the relative path from the project's root directory to the file.
	 * e.g. dir1/.../dir2/filename => The directory structure projectRoot/dir1/.../dir2 will be created,
	 * where the directory dir2 contains the file.
	 * @return The file content
	 */
	public Map<String, String> getOtherFileContent() {
		return new HashMap<String, String>();
	}
}
