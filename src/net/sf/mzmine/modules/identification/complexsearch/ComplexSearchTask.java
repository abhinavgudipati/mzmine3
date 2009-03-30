/*
 * Copyright 2006-2009 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.sf.mzmine.modules.identification.complexsearch;

import java.util.Arrays;
import java.util.logging.Logger;

import net.sf.mzmine.data.ChromatographicPeak;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListRow;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.impl.SimplePeakList;
import net.sf.mzmine.data.impl.SimplePeakListAppliedMethod;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.util.PeakListRowSorter;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.PeakListRowSorter.SortingDirection;
import net.sf.mzmine.util.PeakListRowSorter.SortingProperty;

public class ComplexSearchTask implements Task {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	private TaskStatus status = TaskStatus.WAITING;
	private String errorMessage;

	private int finishedRows, totalRows;
	private PeakList peakList;
	private RawDataFile dataFile;

	private double rtTolerance, mzTolerance, maxComplexHeight;
	private ComplexSearchParameters parameters;

	public static final double hydrogenMass = 1.007825;

	/**
	 * @param parameters
	 * @param peakList
	 */
	public ComplexSearchTask(ComplexSearchParameters parameters,
			PeakList peakList) {

		this.peakList = peakList;
		this.parameters = parameters;
		this.dataFile = peakList.getRawDataFile(0);

		rtTolerance = (Double) parameters
				.getParameterValue(ComplexSearchParameters.rtTolerance);
		mzTolerance = (Double) parameters
				.getParameterValue(ComplexSearchParameters.mzTolerance);
		maxComplexHeight = (Double) parameters
				.getParameterValue(ComplexSearchParameters.maxComplexHeight);

	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#cancel()
	 */
	public void cancel() {
		status = TaskStatus.CANCELED;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getErrorMessage()
	 */
	public String getErrorMessage() {
		return errorMessage;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
	 */
	public double getFinishedPercentage() {
		if (totalRows == 0)
			return 0;
		return ((double) finishedRows) / totalRows;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getStatus()
	 */
	public TaskStatus getStatus() {
		return status;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
	 */
	public String getTaskDescription() {
		return "Identification of complexes in " + peakList;
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	public void run() {

		status = TaskStatus.PROCESSING;

		logger.info("Starting complex search in " + peakList);

		PeakListRow rows[] = peakList.getRows();
		totalRows = rows.length;

		// Sort the array by m/z so we start with biggest peak (possible
		// complex)
		Arrays.sort(rows, new PeakListRowSorter(SortingProperty.MZ,
				SortingDirection.Descending));

		// Compare each three rows against each other
		for (int i = 0; i < totalRows; i++) {

			Range testRTRange = new Range(rows[i].getAverageRT() - rtTolerance,
					rows[i].getAverageRT() + rtTolerance);
			PeakListRow testRows[] = peakList
					.getRowsInsideScanRange(testRTRange);

			for (int j = 0; j < testRows.length; j++) {

				if (j == i)
					continue;

				for (int k = j + 1; k < testRows.length; k++) {

					if (k == i)
						continue;

					// Task canceled?
					if (status == TaskStatus.CANCELED)
						return;

					if (checkComplex(rows[i], testRows[j], testRows[k]))
						addComplexInfo(rows[i], testRows[j], testRows[k]);

				}

			}

			finishedRows++;

		}

		// Add task description to peakList
		((SimplePeakList) peakList)
				.addDescriptionOfAppliedTask(new SimplePeakListAppliedMethod(
						"Identification of complexes", parameters));

		status = TaskStatus.FINISHED;

		logger.info("Finished complexes search in " + peakList);

	}

	/**
	 * Check if candidate peak may be a possible complex of given two peaks
	 * 
	 */
	private boolean checkComplex(PeakListRow complexRow, PeakListRow row1,
			PeakListRow row2) {

		ChromatographicPeak complexPeak = complexRow.getPeak(dataFile);
		ChromatographicPeak peak1 = row1.getPeak(dataFile);
		ChromatographicPeak peak2 = row2.getPeak(dataFile);

		// Check retention time condition
		double rtDifference1 = Math.abs(complexPeak.getRT() - peak1.getRT());
		double rtDifference2 = Math.abs(complexPeak.getRT() - peak2.getRT());
		if ((rtDifference1 > rtTolerance) || (rtDifference2 > rtTolerance))
			return false;

		// Check mass condition
		double expectedMass = peak1.getMZ() + peak2.getMZ() - hydrogenMass;
		double mzDifference = Math.abs(complexPeak.getMZ() - expectedMass);
		if (mzDifference > mzTolerance)
			return false;

		// Check height condition
		if ((complexPeak.getHeight() > peak1.getHeight() * maxComplexHeight)
				|| (complexPeak.getHeight() > peak2.getHeight()
						* maxComplexHeight))
			return false;

		return true;

	}

	/**
	 * Add new identity to the complex row
	 * 
	 * @param mainRow
	 * @param fragmentRow
	 */
	private void addComplexInfo(PeakListRow complexRow, PeakListRow row1,
			PeakListRow row2) {
		ComplexIdentity newIdentity = new ComplexIdentity(complexRow, row1,
				row2);
		complexRow.addPeakIdentity(newIdentity, false);
	}

}