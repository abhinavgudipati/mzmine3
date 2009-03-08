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

package net.sf.mzmine.data;

import net.sf.mzmine.util.Range;

/**
 * This interface defines the properties of a deisotoped peak
 */
public interface IsotopePattern extends ChromatographicPeak, MzDataTable {

    public static final int UNKNOWN_CHARGE = -1;

    /**
     * Returns peaks that form this isotopic pattern
     */
    public ChromatographicPeak[] getOriginalPeaks();

    /**
     * Returns representative peak of this pattern
     */
    public ChromatographicPeak getRepresentativePeak();

    /**
     * Returns the charge of peaks in the pattern
     */
    public int getCharge();

    /**
     * Returns the m/z range of the pattern
     */
    public Range getIsotopeMzRange();

    /**
     * Returns info about this pattern
     */
    public String getIsotopeInfo();

    /**
     * Returns the isotope pattern status.
     */
    public IsotopePatternStatus getIsotopePatternStatus();
    
    /**
     * Returns an array of DataPoint[], that represents all isotopes of this pattern
     */
    public DataPoint[] getIsotopes();

    /**
     * Returns the number of isotopes in this pattern
     */
    public int getNumberOfIsotopes();

    /**
     * Returns the height of the most abundant isotope
     */
    public double getIsotopeHeight();

    /**
     * Returns the chemical formula that represents this pattern
     */
    public String getFormula();
}