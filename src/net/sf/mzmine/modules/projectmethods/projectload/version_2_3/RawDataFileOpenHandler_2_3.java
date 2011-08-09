/*
 * Copyright 2006-2011 The MZmine 2 Development Team
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

package net.sf.mzmine.modules.projectmethods.projectload.version_2_3;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.impl.SimpleMassList;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.projectmethods.projectload.RawDataFileOpenHandler;
import net.sf.mzmine.project.impl.RawDataFileImpl;
import net.sf.mzmine.project.impl.StorableScan;
import net.sf.mzmine.util.ScanUtils;
import net.sf.mzmine.util.StreamCopy;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import de.schlichtherle.util.zip.ZipEntry;
import de.schlichtherle.util.zip.ZipFile;

public class RawDataFileOpenHandler_2_3 extends DefaultHandler implements
		RawDataFileOpenHandler {

	private Logger logger = Logger.getLogger(this.getClass().getName());

	private StringBuffer charBuffer;
	private RawDataFileImpl rawDataFileWriter;
	private int numberOfScans = 0, parsedScans = 0;
	private int scanNumber;
	private int msLevel;
	private int parentScan;
	private int[] fragmentScan;
	private int numberOfFragments;
	private double precursorMZ;
	private int precursorCharge;
	private double retentionTime;
	private boolean centroided;
	private int dataPointsNumber;
	private int stepNumber;
	private int storageFileOffset;
	private int fragmentCount;
	private StreamCopy copyMachine;
	private ArrayList<SimpleMassList> massLists;

	private boolean canceled = false;

	/**
	 * Extract the scan file and copies it into the temporary folder. Create a
	 * new raw data file using the information from the XML raw data description
	 * file
	 * 
	 * @param Name
	 *            raw data file name
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	public RawDataFile readRawDataFile(ZipFile zipFile, ZipEntry scansEntry,
			ZipEntry xmlEntry) throws IOException,
			ParserConfigurationException, SAXException {

		stepNumber = 0;
		numberOfScans = 0;
		parsedScans = 0;
		storageFileOffset = 0;

		charBuffer = new StringBuffer();
		massLists = new ArrayList<SimpleMassList>();

		// Writes the scan file into a temporary file
		logger.info("Moving scan file : " + scansEntry.getName()
				+ " to the temporary folder");

		rawDataFileWriter = (RawDataFileImpl) MZmineCore.createNewFile(null);

		File scanFile = rawDataFileWriter.getScanFile();

		InputStream scanInputStream = zipFile.getInputStream(scansEntry);
		FileOutputStream fileStream = new FileOutputStream(scanFile);

		// Extracts the scan file from the zip project file to the temporary
		// folder
		copyMachine = new StreamCopy();
		stepNumber++;
		copyMachine.copy(scanInputStream, fileStream, scansEntry.getSize());
		fileStream.close();

		rawDataFileWriter.openScanFile(scanFile);

		stepNumber++;

		// Reads the XML file (raw data description)
		InputStream xmlInputStream = zipFile.getInputStream(xmlEntry);
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();
		saxParser.parse(xmlInputStream, this);

		// Adds the raw data file to MZmine
		RawDataFile rawDataFile = rawDataFileWriter.finishWriting();
		return rawDataFile;

	}

	/**
	 * @return the progress of these functions loading the raw data from the zip
	 *         file
	 */
	public double getProgress() {

		switch (stepNumber) {
		case 1:
			// We can estimate that copying the scan file takes ~75% of the time
			return copyMachine.getProgress() * 0.75;
		case 2:
			if (numberOfScans == 0)
				return 0;
			return ((double) parsedScans / numberOfScans) * 0.25 + 0.75;
		default:
			return 0.0;
		}
	}

	public void cancel() {
		canceled = true;
		if (copyMachine != null)
			copyMachine.cancel();
	}

	/**
	 * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String,
	 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String lName, String qName,
			Attributes attrs) throws SAXException {

		if (canceled)
			throw new SAXException("Parsing canceled");

		if (qName.equals(RawDataElementName_2_3.QUANTITY_FRAGMENT_SCAN
				.getElementName())) {
			numberOfFragments = Integer
					.parseInt(attrs.getValue(RawDataElementName_2_3.QUANTITY
							.getElementName()));
			if (numberOfFragments > 0) {
				fragmentScan = new int[numberOfFragments];
				fragmentCount = 0;
			}
		}

		if (qName.equals(RawDataElementName_2_3.MASS_LIST.getElementName())) {
			String name = attrs.getValue(RawDataElementName_2_3.NAME
					.getElementName());
			SimpleMassList newML = new SimpleMassList(name, null, null);
			massLists.add(newML);
		}
	}

	/**
	 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
	 *      java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String sName, String qName)
			throws SAXException {

		if (canceled)
			throw new SAXException("Parsing canceled");

		// <NAME>
		if (qName.equals(RawDataElementName_2_3.NAME.getElementName())) {

			// Adds the scan file and the name to the new raw data file
			String name = getTextOfElement();
			logger.info("Loading raw data file: " + name);
			rawDataFileWriter.setName(name);
		}

		if (qName.equals(RawDataElementName_2_3.QUANTITY_SCAN.getElementName())) {
			numberOfScans = Integer.parseInt(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.SCAN_ID.getElementName())) {
			scanNumber = Integer.parseInt(getTextOfElement());
			parsedScans++;
		}

		if (qName.equals(RawDataElementName_2_3.MS_LEVEL.getElementName())) {
			msLevel = Integer.parseInt(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.PARENT_SCAN.getElementName())) {
			parentScan = Integer.parseInt(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.PRECURSOR_MZ.getElementName())) {
			precursorMZ = Double.parseDouble(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.PRECURSOR_CHARGE
				.getElementName())) {
			precursorCharge = Integer.parseInt(getTextOfElement());
		}

		if (qName
				.equals(RawDataElementName_2_3.RETENTION_TIME.getElementName())) {
			retentionTime = Double.parseDouble(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.CENTROIDED.getElementName())) {
			centroided = Boolean.parseBoolean(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.QUANTITY_DATAPOINTS
				.getElementName())) {
			dataPointsNumber = Integer.parseInt(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.FRAGMENT_SCAN.getElementName())) {
			fragmentScan[fragmentCount++] = Integer
					.parseInt(getTextOfElement());
		}

		if (qName.equals(RawDataElementName_2_3.MASS_LIST.getElementName())) {
			char encodedDataPoints[] = getTextOfElement().toCharArray();
			try {
				DataPoint dataPoints[] = ScanUtils
						.decodeDataPointsBase64(encodedDataPoints);
				SimpleMassList newML = massLists.get(massLists.size() - 1);
				newML.setDataPoints(dataPoints);
			} catch (IOException e) {
				throw new SAXException(e);
			}

		}

		if (qName.equals(RawDataElementName_2_3.SCAN.getElementName())) {

			StorableScan storableScan = new StorableScan(rawDataFileWriter,
					storageFileOffset, dataPointsNumber, scanNumber, msLevel,
					retentionTime, parentScan, precursorMZ, precursorCharge,
					fragmentScan, centroided);

			for (SimpleMassList newML : massLists) {
				newML.setScan(storableScan);
				storableScan.addMassList(newML);
			}
			massLists.clear();

			try {
				rawDataFileWriter.addScan(storableScan);
			} catch (IOException e) {
				throw new SAXException(e);
			}
			storageFileOffset += dataPointsNumber * 4 * 2;

		}
	}

	/**
	 * Return a string without tab an EOF characters
	 * 
	 * @return String element text
	 */
	private String getTextOfElement() {
		String text = charBuffer.toString();
		text = text.replaceAll("[\n\r\t]+", "");
		text = text.replaceAll("^\\s+", "");
		charBuffer.delete(0, charBuffer.length());
		return text;
	}

	/**
	 * characters()
	 * 
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char buf[], int offset, int len) throws SAXException {
		charBuffer = charBuffer.append(buf, offset, len);
	}
}
