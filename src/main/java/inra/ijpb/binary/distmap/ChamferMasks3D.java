/*-
 * #%L
 * Mathematical morphology library and plugins for ImageJ/Fiji.
 * %%
 * Copyright (C) 2014 - 2017 INRA.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
package inra.ijpb.binary.distmap;

import inra.ijpb.binary.BinaryImages;


/**
 * <p>
 * A pre-defined set of weights that can be used to compute 3D distance 
 * transform using chamfer approximations of euclidean metric.
 * </p>
 * 
 * <p>
 * Provides methods to access weight values either as float array or as short
 * array.
 * </p>
 * 
 * <p>
 * Example of use:
 * <pre><code>
 * float[] floatWeights = ChamferWeights3D2.BORGEFORS.getFloatWeights();
 * boolean normalize = true;
 * DistanceTransform3D dt = new DistanceTransform3DFloat(floatWeights, normalize);
 * ImageStack result = dt.distanceMap(inputStack);
 * </code></pre>
 *
 * 
 * @see BinaryImages#distanceMap(ij.ImageStack)
 * @see inra.ijpb.binary.distmap.ChamferDistanceTransform3DShort
 */
public enum ChamferMasks3D
{
	/** Use weight equal to 1 for all neighbors */
	CHESSBOARD("Chessboard (1,1,1)", ChamferMask3D.CHESSBOARD), 
	/**
	 * Use weights 1 for orthogonal neighbors and 2 for diagonal neighbors,
	 * and 3 for cube-diagonals.
	 */
	CITY_BLOCK("City-Block (1,2,3)", ChamferMask3D.CITY_BLOCK),
	/**
	 * Use floating-point weights 1.0 for orthogonal neighbors and sqrt(2) for
	 * diagonal neighbors, and sqrt(3) for cube-diagonals.
	 * 
	 * Use 10, 14 and 17 for the 16-bits integer version.
	 */
	QUASI_EUCLIDEAN("Quasi-Euclidean (1,1.41,1.73)", ChamferMask3D.QUASI_EUCLIDEAN),
	/**
	 * Use weights 3 for orthogonal neighbors and 4 for diagonal neighbors,
	 * and 5 for cube-diagonals (best approximation for 3-by-3-by-3 masks).
	 */
	BORGEFORS("Borgefors (3,4,5)", ChamferMask3D.BORGEFORS),

	/**
	 * Use weights 3 for orthogonal neighbors and 4 for diagonal neighbors, and
	 * 5 for cube-diagonals, and 7 for (2,1,1) shifts. Good approximation using
	 * only four weights, and keeping low value of orthogonal weight.
	 */
	SVENSSON_3_4_5_7("Svensson <3,4,5,7>", ChamferMask3D.SVENSSON_3_4_5_7),

	/**
	 * Use five types of weights.
	 */
	WEIGHTS_8_11_14_18_20("<8,11,14,18,20>", new ChamferMask3DW5(8, 11, 14, 18, 20)),

	/**
	 * Use five types of weights.
	 */
	WEIGHTS_13_18_22_29_31("<13,18,22,29,31>", new ChamferMask3DW5(13, 18, 22, 29, 31));

	private final String label;
	private final ChamferMask3D mask;

	private ChamferMasks3D(String label, ChamferMask3D mask)
	{
		this.label = label;
		this.mask = mask;		
	}
	
	private ChamferMasks3D(String label, short[] shortWeights)
	{
		this.label = label;
		if (shortWeights.length == 3)
		{
			this.mask = new ChamferMask3DW3(shortWeights);
		}
		else if (shortWeights.length == 4)
		{
			this.mask = new ChamferMask3DW4(shortWeights);
		}
		else
		{
			throw new RuntimeException("The length of the weights must be either 3 or 4.");
		}
	}

	private ChamferMasks3D(String label, short[] shortWeights,
			float[] floatWeights)
	{
		this.label = label;
		if (shortWeights.length == 3 && floatWeights.length == 3)
		{
			this.mask = new ChamferMask3DW3Float(shortWeights, floatWeights);
		}
		else
		{
			throw new RuntimeException("The length of both weight arrays must be 3.");
		}
	}

	public ChamferMask3D getMask()
	{
		return this.mask;
	}

	public String toString()
	{
		return this.label;
	}

	public static String[] getAllLabels()
	{
		int n = ChamferMasks3D.values().length;
		String[] result = new String[n];

		int i = 0;
		for (ChamferMasks3D weight : ChamferMasks3D.values())
			result[i++] = weight.label;

		return result;
	}

	/**
	 * Determines the operation type from its label.
	 * 
	 * @param label the name of a chamfer weight 
	 * @return the ChamferWeights3D2 enum corresponding to the given name
	 * 
	 * @throws IllegalArgumentException
	 *             if label name is not recognized.
	 */
	public static ChamferMasks3D fromLabel(String label)
	{
		if (label != null)
			label = label.toLowerCase();
		for (ChamferMasks3D weight : ChamferMasks3D.values())
		{
			String cmp = weight.label.toLowerCase();
			if (cmp.equals(label))
				return weight;
		}
		throw new IllegalArgumentException(
				"Unable to parse ChamferWeights3D2 with label: " + label);
	}
}
