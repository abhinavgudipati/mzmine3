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
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.gapfilling.samerange;

import java.util.TreeMap;

import net.sf.mzmine.data.ChromatographicPeak;
import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.PeakStatus;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.util.CollectionUtils;
import net.sf.mzmine.util.MathUtils;
import net.sf.mzmine.util.PeakUtils;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.ScanUtils;

/**
 * This class represents a manually picked chromatographic peak.
 */
class SameRangePeak implements ChromatographicPeak {

	private RawDataFile dataFile;

	// Raw M/Z, RT, Height and Area
	private double mz, rt, height, area;

	// Boundaries of the peak
	private Range rtRange, mzRange, intensityRange;

	// Map of scan number and data point
	private TreeMap<Integer, DataPoint> mzPeakMap;

	// Number of most intense fragment scan
	private int fragmentScanNumber, representativeScan;

	/**
	 * Initializes empty peak for adding data points
	 */
	SameRangePeak(RawDataFile dataFile) {
		this.dataFile = dataFile;
		mzPeakMap = new TreeMap<Integer, DataPoint>();
	}

	/**
	 * This peak is always a result of manual peak detection, therefore MANUAL
	 */
	public PeakStatus getPeakStatus() {
		return PeakStatus.ESTIMATED;
	}

	/**
	 * This method returns M/Z value of the peak
	 */
	public double getMZ() {
		return mz;
	}

	/**
	 * This method returns retention time of the peak
	 */
	public double getRT() {
		return rt;
	}

	/**
	 * This method returns the raw height of the peak
	 */
	public double getHeight() {
		return height;
	}

	/**
	 * This method returns the raw area of the peak
	 */
	public double getArea() {
		return area;
	}

	/**
	 * This method returns numbers of scans that contain this peak
	 */
	public int[] getScanNumbers() {
		return CollectionUtils.toIntArray(mzPeakMap.keySet());
	}

	/**
	 * This method returns a representative datapoint of this peak in a given
	 * scan
	 */
	public DataPoint getDataPoint(int scanNumber) {
		return mzPeakMap.get(scanNumber);
	}

	public Range getRawDataPointsIntensityRange() {
		return intensityRange;
	}

	public Range getRawDataPointsMZRange() {
		return mzRange;
	}

	public Range getRawDataPointsRTRange() {
		return rtRange;
	}

	/**
	 * @see net.sf.mzmine.data.ChromatographicPeak#getDataFile()
	 */
	public RawDataFile getDataFile() {
		return dataFile;
	}

	public String toString() {
		return PeakUtils.peakToString(this);
	}

	/**
	 * Adds a new data point to this peak
	 * 
	 * @param scanNumber
	 * @param dataPoints
	 * @param rawDataPoints
	 */
	void addDatapoint(int scanNumber, DataPoint dataPoint) {

		double rt = dataFile.getScan(scanNumber).getRetentionTime();

		if (mzPeakMap.isEmpty()) {
			rtRange = new Range(rt);
			mzRange = new Range(dataPoint.getMZ());
			intensityRange = new Range(dataPoint.getIntensity());
		} else {
			rtRange.extendRange(rt);
			mzRange.extendRange(dataPoint.getMZ());
			intensityRange.extendRange(dataPoint.getIntensity());
		}

		mzPeakMap.put(scanNumber, dataPoint);

	}

	void finalizePeak() {

		// Trim the zero-intensity data points from the beginning and end
		while (!mzPeakMap.isEmpty()) {
			int scanNumber = mzPeakMap.firstKey();
			if (mzPeakMap.get(scanNumber).getIntensity() > 0)
				break;
			mzPeakMap.remove(scanNumber);
		}
		while (!mzPeakMap.isEmpty()) {
			int scanNumber = mzPeakMap.lastKey();
			if (mzPeakMap.get(scanNumber).getIntensity() > 0)
				break;
			mzPeakMap.remove(scanNumber);
		}

		// Check if we have any data points
		if (mzPeakMap.isEmpty()) {
			throw (new IllegalStateException(
					"Peak can not be finalized without any data points"));
		}

		// Get all scan numbers
		int allScanNumbers[] = CollectionUtils.toIntArray(mzPeakMap.keySet());

		// Find the data point with top intensity and use its RT and height
		for (int i = 0; i < allScanNumbers.length; i++) {
			DataPoint dataPoint = mzPeakMap.get(allScanNumbers[i]);
			double rt = dataFile.getScan(allScanNumbers[i]).getRetentionTime();
			if (dataPoint.getIntensity() > height) {
				height = dataPoint.getIntensity();
				representativeScan = allScanNumbers[i];
				this.rt = rt;
			}
		}

		// Calculate peak area
		area = 0f;
		for (int i = 1; i < allScanNumbers.length; i++) {

			// X axis interval length
			double previousRT = dataFile.getScan(allScanNumbers[i - 1])
					.getRetentionTime();
			double thisRT = dataFile.getScan(allScanNumbers[i])
					.getRetentionTime();
			double rtDifference = thisRT - previousRT;

			// Intensity at the beginning and end of the interval
			double previousIntensity = mzPeakMap.get(allScanNumbers[i - 1])
					.getIntensity();
			double thisIntensity = mzPeakMap.get(allScanNumbers[i])
					.getIntensity();
			double averageIntensity = (previousIntensity + thisIntensity) / 2;

			// Calculate area of the interval
			area += (rtDifference * averageIntensity);

		}

		// Calculate median MZ
		double mzArray[] = new double[allScanNumbers.length];
		for (int i = 0; i < allScanNumbers.length; i++) {
			mzArray[i] = mzPeakMap.get(allScanNumbers[i]).getMZ();
		}
		this.mz = MathUtils.calcQuantile(mzArray, 0.5f);

		fragmentScanNumber = ScanUtils.findBestFragmentScan(dataFile, rtRange,
				mzRange);

	}

	public void setMZ(double mz) {
		this.mz = mz;
	}

	public int getRepresentativeScanNumber() {
		return representativeScan;
	}

	public int getMostIntenseFragmentScanNumber() {
		return fragmentScanNumber;
	}
}
