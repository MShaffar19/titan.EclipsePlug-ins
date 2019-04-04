/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.log.viewer.views.details;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.titan.log.viewer.models.LogFileMetaData;
import org.eclipse.titan.log.viewer.parsers.data.TestCase;
import org.eclipse.titan.log.viewer.readers.CachedLogReader;

/**
 * Class for details data
 *
 */
public class StatisticalData {

	private LogFileMetaData logFileMetaData;
	private List<TestCase> testCaseVector;
	private CachedLogReader cachedLogFileReader;

	public StatisticalData(final LogFileMetaData logFileMetaData, final List<TestCase> testCaseVector, final CachedLogReader cachedLogFileReader) {
		this.logFileMetaData = logFileMetaData;
		this.testCaseVector = new ArrayList<TestCase>();
		for (final TestCase tc : testCaseVector) {
			if (!tc.isControlPart()) {
				this.testCaseVector.add(tc);
			}
		}
		this.cachedLogFileReader = cachedLogFileReader;
	}

	public CachedLogReader getCachedLogFileReader() {
		return cachedLogFileReader;
	}
	public void setCachedLogFileReader(final CachedLogReader cachedLogFileReader) {
		this.cachedLogFileReader = cachedLogFileReader;
	}
	public LogFileMetaData getLogFileMetaData() {
		return logFileMetaData;
	}
	public void setLogFileMetaData(final LogFileMetaData logFileMetaData) {
		this.logFileMetaData = logFileMetaData;
	}
	public List<TestCase> getTestCaseVector() {
		return testCaseVector;
	}
	public void setTestCaseVector(final List<TestCase> testCaseVector) {
		this.testCaseVector = new ArrayList<TestCase>();
		for (final TestCase tc : testCaseVector) {
			if (!tc.isControlPart()) {
				this.testCaseVector.add(tc);
			}
		}
	}
}
