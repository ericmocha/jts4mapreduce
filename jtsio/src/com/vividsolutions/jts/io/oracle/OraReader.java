/*
 * The JTS Topology Suite is a collection of Java classes that
 * implement the fundamental operations required to validate a given
 * geo-spatial data set to a known topological specification.
 *
 * Copyright (C) 2001 Vivid Solutions
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * For more information, contact:
 *
 *     Vivid Solutions
 *     Suite #1A
 *     2328 Government Street
 *     Victoria BC  V8T 5G5
 *     Canada
 *
 *     (250)385-6040
 *     www.vividsolutions.com
 */
/*
 *    Geotools2 - OpenSource mapping toolkit
 *    http://geotools.org
 *    (C) 2003, Geotools Project Managment Committee (PMC)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 */
package com.vividsolutions.jts.io.oracle;

import java.sql.SQLException;
import java.util.*;

import oracle.sql.*;

import com.vividsolutions.jts.algorithm.CGAlgorithms;
import com.vividsolutions.jts.geom.*;

/**
 * Reads a {@link Geometry} from an Oracle <code>MDSYS.GEOMETRY</code> <code>STRUCT</code> object.
 * <p>
 * A {@link GeometryFactory} may be provided, otherwise a default one will be used.
 * If a {@link PrecisionModel} other than {@link PrecisionModel#FLOATING} 
 * is supplied it is the callers's responsibility
 * to ensure that it matches the precision of the incoming data.
 * If a lower precision for the data is required, a subsequent
 * process must be run on the data to reduce its precision.
 * <p>
 * The following Oracle geometry types are supported:
 * <ul>
 * <li>POINT, MULTIPOINT
 * <li>LINE, MULTILINE
 * <li>POLYGON, MULTIPOLYGON
 * </ul>
 * The optimized representations of <code>SDO_POINT</code> 
 * and <code>RECTANGLE</code> are supported:
 * <ul>
 * <li>If the <code>SDO_POINT</code> attribute is present 
 * and <code>SDO_ELEM_INFO</code> and <code>SDO_ORDINATES</code> are not,
 * a {@link Point} geometry is read.
 * Otherwise, the geometry specified by the latter two attributes is read.
 * <li><code>RECTANGLE</code>s are converted to equivalent {@link Polygon}s
 * </ul> 
 * The Geometry SRID field is populated from the input Geometry.
 * <p>
 * To use this class a suitable Oracle JDBC JAR must be present in the classpath.
 * 
 * <h3>LIMITATIONS</h3>
 * <ul>
 * <li>4-dimensional (XYZM) Oracle geometries are not supported
 * <li>Oracle geometries with a GTYPE of <code>43xx</code> (XYMZ) are not supported.
 * <li>The number of dimension read is limited by the number supported
 * by the provided {@link CoordinateSequenceFactory}
 * <li>Geometries containing arcs are not supported
 * <li>Surface and solid geometries are not supported
 * <li>There is currently no way to read ancillary SDO_POINT information
 * </ul>
 *
 * @author Martin Davis
 */
public class OraReader {
	
	private static final int NULL_DIMENSION = -1;
	
	private GeometryFactory geometryFactory;

	private int dimension = NULL_DIMENSION;

	/**
	 * Creates a new reader, with a default {@link GeometryFactory}.
	 *
	 * @see #OraReader(GeometryFactory)
	 */
	public OraReader() {
		this(new GeometryFactory());
	}

	/**
	 * Creates a new reader, with the supplied {@link GeometryFactory}.
	 * It is assumed that the supplied {@link PrecisionModel}
	 * matches the precision of the incoming data -
	 * coordinates are <b>not</b> made precise when read.
	 *
	 * @param gf A non-null geometry factory for later use.
	 *
	 * @throws NullPointerException when the geometry factory is null.
	 */
	public OraReader(GeometryFactory gf) {
		if (gf == null)
			throw new NullPointerException("Geometry Factory may not be Null");
		this.geometryFactory = gf;
	}
	
	/**
	 * Gets the number of coordinate dimensions which will be read.
	 *
	 * @return the dimension which will be read
	 */
	public int getDimension() {
		return dimension;
	}

	/**
	 * Sets the number of coordinate dimensions to read.
	 *
	 * @param dimension the dimension to read
	 */
	public void setDimension(int dimension) {
		this.dimension = dimension;
	}

	/**
	 * Reads a {@link Geometry} representing the MDSYS.GEOMETRY
	 * provided in the STRUCT. The type of geometry created 
	 * depends on the Geometry type specified within the STRUCT.
	 * The SRID of the created geometry is set to be the same as the input SRID.
	 *
	 * @param struct The MDSYS.GEOMETRY Object to decode
	 * @return A Geometry if one could be created, null otherwise
	 * 
	 * @throws SQLException if a read error occurs while accessing the struct
   * @throws IllegalArgumentException if an unsupported geometry type or encoding error is found
	 */
	public Geometry read(STRUCT struct) throws SQLException 
	{
		// Return null for null input
		if (struct == null)
			return null;

		Datum data[] = struct.getOracleAttributes();
		
		int gType = OraUtil.toInteger(data[0], 0);
		int SRID = OraUtil.toInteger(data[1], OraGeom.SRID_NULL);
		double point[] = OraUtil.toDoubleArray((STRUCT) data[2], Double.NaN);
		int elemInfo[] = OraUtil.toIntArray((ARRAY) data[3], 0);
		double ordinates[] = OraUtil.toDoubleArray((ARRAY) data[4], Double.NaN);
		OraGeom oraGeom = new OraGeom(gType, SRID, point, elemInfo, ordinates);
		Geometry geom = read(oraGeom);
		
		// Set SRID of created Geometry to be the same as input (regardless of geomFactory SRID)
		if (geom != null)
			geom.setSRID(SRID);
		return geom;
	}

	/**
   * Reads a {@link Geometry} from SDO_GEOMETRY attributes.
   *
   * @param oraGeom the Oracle geometry to read
   * @return Geometry read
   * @throws IllegalArgumentException when an encoding error or unsupported geometry type is found
   */
	Geometry read(OraGeom oraGeom) {
    int ordDim = oraGeom.ordDim();
    if (ordDim < 2) {
    	throw new IllegalArgumentException("Dimension D = " + ordDim + " is not supported by JTS. " +
    			"Either specify a valid dimension or use Oracle Locator Version 9i or later");
    }
    // The actual dimension read is the smaller of the explicit dimension and the input dimension
    int outputDim = ordDim;
    if(dimension != NULL_DIMENSION){
    	outputDim = dimension;
    }    
    // read from SDO_POINT_TYPE, if that carries the primary geometry data
    if (oraGeom.isCompactPoint()) {
      CoordinateSequence ptCoord = coordinates(geometryFactory.getCoordinateSequenceFactory(), outputDim, ordDim, oraGeom.point, false);
      return createPoint(ptCoord);
    } 
    
    CoordinateSequence coords = coordinates(geometryFactory.getCoordinateSequenceFactory(), outputDim,  ordDim, oraGeom.ordinates, true);

    switch (oraGeom.geomType()) {
    case OraGeom.GEOM_TYPE.POINT:
        return readPoint(oraGeom, 0, coords);
    case OraGeom.GEOM_TYPE.LINE:
        return readLine(oraGeom, 0, coords);
    case OraGeom.GEOM_TYPE.POLYGON:
        return readPolygon(oraGeom, 0, coords);
    case OraGeom.GEOM_TYPE.MULTIPOINT:
        return readMultiPoint(oraGeom, 0, coords);
    case OraGeom.GEOM_TYPE.MULTILINE:
        return readMultiLine(oraGeom, coords);
    case OraGeom.GEOM_TYPE.MULTIPOLYGON:
        return readMultiPolygon(oraGeom, coords);
    case OraGeom.GEOM_TYPE.COLLECTION:
        return readCollection(oraGeom, coords);
    default:
    	throw new IllegalArgumentException("GTYPE " + oraGeom.gType + " is not supported");
    }
  }

	/**
   * Constructs a coordinate sequence from the ordinates
   * taking into account the input and output dimensions.
   *
   * The number of ordinates read per coordinate is given by outputDim.
   * The number of ordinates for each input point is given by ordDim.
   * The ordinate array length must be a multiple of this value.
   *
   * @param csFactory CoordinateSequenceFactory used to encode ordinates for JTS
   * @param outputDim dimension of target coordinate sequence
   * @param ordDim dimension of input coordinates
   * @param ordinates the ordinate values to read
   * @param checkValidOrdLength whether to check the ordinates length
   *
   * @return a coordinate sequence representing all the read ordinates
   *
   * @throws IllegalArgumentException if the ordinate array has an invalid length
   */
  private CoordinateSequence coordinates(CoordinateSequenceFactory csFactory,
      int outputDim, int ordDim, double[] ordinates, boolean checkValidOrdLength)
  {
    // handle empty case
    if ((ordinates == null) || (ordinates.length == 0)) {
      return csFactory.create(new Coordinate[0]);
    }
    if (checkValidOrdLength) {
      if ((ordDim == 0 && ordinates.length != 0)
          || (ordDim != 0 && ((ordinates.length % ordDim) != 0))) {
        throw new IllegalArgumentException("SDO_GTYPE Dimension " + ordDim
            + " is inconsistent with SDO_ORDINATES length " + ordinates.length);
      }
    }
    int nCoord = (ordDim == 0 ? 0 : ordinates.length / ordDim);

    int csDim = outputDim;
    CoordinateSequence cs = csFactory.create(nCoord, csDim);
    int actualCSDim = cs.getDimension();
    int readDim = Math.min(actualCSDim, ordDim);
    
    for (int iCoord = 0; iCoord < nCoord; iCoord++) {
      int iOrd = 0;
      for (; iOrd < readDim; iOrd++) {
        cs.setOrdinate(iCoord, iOrd, ordinates[iCoord * ordDim + iOrd]);
      }
    }
    return cs;
  }

  /**
   * Create a {@link GeometryCollection} as specified by elemInfo. Note that in
   * Oracle, unlike the SFS and JTS, collections contain only atomic types or
   * (possibly) MultiPoints. This makes them simpler to parse.
   * 
   * @param oraGeom
   *          SDO_GEOMETRY attributes being read
   * @param coords
   *          the coordinates of the entire geometry
   * @return GeometryCollection
   * 
   * @throws IllegalArgumentException
   *           when an encoding error or unsupported geometry type is found
   */
    private GeometryCollection readCollection(OraGeom oraGeom, CoordinateSequence coords) 
    {
      checkOrdinates(oraGeom, 0, "GeometryCollection");
      
      int nElem = oraGeom.numElements();
      List geomList = new ArrayList();
      boolean cont = true;
      for (int i = 0; cont && i < nElem; i++) {
        int etype = oraGeom.eType(i);
        int interpretation = oraGeom.interpretation(i);
        Geometry geom;

        switch (etype) {
        case -1:
          cont = false; // We are at the end of the list - get out of here
          continue;
            
        case OraGeom.ETYPE.POINT:
            if (interpretation == OraGeom.INTERP.POINT) {
                geom = readPoint(oraGeom, i, coords);
            } else if (interpretation > 1) {
                geom = readMultiPoint(oraGeom, i, coords);
            } else {
                throw new IllegalArgumentException("ETYPE.POINT requires INTERPRETATION >= 1");
            }
            break;

        case OraGeom.ETYPE.LINE:
            geom = readLine(oraGeom, i, coords);
            break;

        case OraGeom.ETYPE.POLYGON:
        case OraGeom.ETYPE.POLYGON_EXTERIOR:
            geom = readPolygon(oraGeom, i, coords);
            i += ((Polygon) geom).getNumInteriorRing();
            break;

        case OraGeom.ETYPE.POLYGON_INTERIOR:
            throw new IllegalArgumentException(
                "ETYPE 2003 (Polygon Interior) not expected in a Collection");

        default:
            throw new IllegalArgumentException("ETYPE " + etype
                + " not representable as a JTS Geometry."
                + "(Custom and Compound Straight and Curved Geometries not supported)");
        }
        geomList.add(geom);
      }
      GeometryCollection geoms = geometryFactory.createGeometryCollection(GeometryFactory.toGeometryArray(geomList));
      return geoms;
    }

    /**
     * Create MultiPolygon as encoded by elemInfo.
     *
     * @param oraGeom SDO_GEOMETRY attributes being read
     * @param coords the coordinates of the entire geometry
     * @return MultiPolygon
     */
    private MultiPolygon readMultiPolygon(OraGeom oraGeom, CoordinateSequence coords)
    {
      int nElem = oraGeom.numElements();
      List geoms = new ArrayList();
      for (int i = 0; i < nElem; i++) {
        int etype = oraGeom.eType(i);
        if ((etype == OraGeom.ETYPE.POLYGON) || (etype == OraGeom.ETYPE.POLYGON_EXTERIOR)) {
          Polygon poly = readPolygon(oraGeom, i, coords);
          i += poly.getNumInteriorRing(); // skip interior rings
          geoms.add(poly);
        } 
        else { // not a Polygon - stop reading
        	break;
        }
      }
      MultiPolygon polys = geometryFactory.createMultiPolygon(GeometryFactory.toPolygonArray(geoms));
      return polys;
    }

    /**
     * Create MultiLineString as encoded by elemInfo.
     *
     * @param oraGeom SDO_GEOMETRY attributes being read
     * @param coords the coordinates of the entire geometry
     * @return MultiLineString
     */
    private MultiLineString readMultiLine(OraGeom oraGeom, CoordinateSequence coords) 
    {
      int nElem = oraGeom.numElements();
      List geoms = new ArrayList();
      for (int i = 0; i < nElem; i++) {
        int etype = oraGeom.eType(i);
        // stop reading if not a line
        if (etype != OraGeom.ETYPE.LINE)
          break;
        geoms.add(readLine(oraGeom, i, coords));
      }
      MultiLineString lines = geometryFactory.createMultiLineString(GeometryFactory.toLineStringArray(geoms));
      return lines;
    }

    /**
     * Create MultiPoint as encoded by elemInfo.
     *
     * @param oraGeom SDO_GEOMETRY attributes being read
     * @param elemIndex the element being read
     * @param coords the coordinates of the entire geometry
     * @return MultiPoint
     */
    private MultiPoint readMultiPoint(OraGeom oraGeom, int elemIndex, CoordinateSequence coords) 
    {
      int etype = oraGeom.eType(elemIndex);
      int interpretation = oraGeom.interpretation(elemIndex);

      checkOrdinates(oraGeom, elemIndex, "MultiPoint");
      checkETYPE(etype, OraGeom.ETYPE.POINT, "MultiPoint");
      // MultiPoints have a unique interpretation code
      if (! (interpretation > 1)){
        errorInterpretation(interpretation, "MultiPoint");
      }
      int start = coordIndex(oraGeom, elemIndex);
      int end = coordIndex(oraGeom, elemIndex + 1);
      MultiPoint points = geometryFactory.createMultiPoint(subSeq(coords, start, end));
      return points;
    }
    
    /**
     * Read {@link Polygon) from encoded geometry.
     *
     * @param oraGeom SDO_GEOMETRY attributes being read
     * @param elemIndex the element being read
     * @param coords the coordinates of the entire geometry
     * @return Polygon as encoded by elemInfo, or null when faced with and
     *         encoding that can not be captured by JTS
     * @throws IllegalArgumentException When faced with an invalid SDO encoding
     */
    private Polygon readPolygon(OraGeom oraGeom, int elemIndex, CoordinateSequence coords) 
    {
      int etype = oraGeom.eType(elemIndex);
      int interpretation = oraGeom.interpretation(elemIndex);

    	checkOrdinates(oraGeom, elemIndex, "Polygon");
    	checkETYPE(etype,OraGeom.ETYPE.POLYGON, OraGeom.ETYPE.POLYGON_EXTERIOR, "Polygon");
    	checkInterpretation(interpretation, OraGeom.INTERP.POLYGON, OraGeom.INTERP.RECTANGLE, "Polygon");

      int nElem = oraGeom.numElements();
    	// ETYPE is either POLYGON or POLYGON_EXTERIOR
      LinearRing exteriorRing = readLinearRing(oraGeom, elemIndex, coords);

      /**
       * Holes are read as long as ETYPE = POLYGON_INTERIOR
       * or ETYPE = POLYGON && orient = CW (Hole)
       */
      List holeRings = new ArrayList();
      for (int i = elemIndex + 1; i < nElem; i++) {
        etype = oraGeom.eType(i);
        if (etype == OraGeom.ETYPE.POLYGON_INTERIOR) {
          holeRings.add(readLinearRing(oraGeom, i, coords));
        } 
        else if (etype == OraGeom.ETYPE.POLYGON) { 
          // test orientation of Ring to see if it is
          // an interior (hole) ring
          LinearRing ring = readLinearRing(oraGeom, i, coords);
          boolean isHole = ! CGAlgorithms.isCCW(ring.getCoordinates());
          if (! isHole)
            break;// it is an Interior Hole
          holeRings.add(ring);
        } 
        else { // not a LinearRing - get out of here
            break;
        }
      }
      Polygon poly = geometryFactory.createPolygon(exteriorRing, 
          GeometryFactory.toLinearRingArray(holeRings));
      return poly;
    }

    /**
     * Create LinearRing for exterior/interior polygon ELEM_INFO triplets.
     *
     * @param oraGeom SDO_GEOMETRY attributes being read
     * @param elemIndex the element being read
     * @param coords the coordinates of the entire geometry
     * @return LinearRing
     *
     * @throws IllegalArgumentException If circle, or curve is requested
     */
    private LinearRing readLinearRing(OraGeom oraGeom, int elemIndex, CoordinateSequence coords) 
    {
      int etype = oraGeom.eType(elemIndex);
      int interpretation = oraGeom.interpretation(elemIndex);

    	checkOrdinates(oraGeom, elemIndex, "Polygon");
    	checkETYPE(etype,OraGeom.ETYPE.POLYGON, OraGeom.ETYPE.POLYGON_EXTERIOR,  OraGeom.ETYPE.POLYGON_INTERIOR, "Polygon");
    	checkInterpretation(interpretation, OraGeom.INTERP.POLYGON, OraGeom.INTERP.RECTANGLE, "Polygon");

      int start = coordIndex(oraGeom, elemIndex);
      int end = coordIndex(oraGeom, elemIndex + 1);
      CoordinateSequence seq = subSeq(coords, start, end);
    	LinearRing ring;
      if (interpretation == OraGeom.INTERP.POLYGON) {
        ring = geometryFactory.createLinearRing(seq);
      } 
      else { 
      	// interpretation == OraSDO.INTERP.RECTANGLE
        // rectangle does not maintain measures
        Coordinate min = seq.getCoordinate(0);
        Coordinate max = seq.getCoordinate(1);
        ring = geometryFactory.createLinearRing(new Coordinate[] {
                    min, new Coordinate(max.x, min.y), 
                    max, new Coordinate(min.x, max.y), 
                    min
                });
      }
      return ring;
    }

  /**
   * Create LineString as encoded.
   * 
   * @param oraGeom SDO_GEOMETRY attributes being read
   * @param elemIndex the element being read
   * @param coords the coordinates of the entire geometry
   * @return LineString
   * 
   * @throws IllegalArgumentException If asked to create a curve
   */
  private LineString readLine(OraGeom oraGeom, int elemIndex, CoordinateSequence coords)
  {
    int etype = oraGeom.eType(elemIndex);
    int interpretation = oraGeom.interpretation(elemIndex);

  	checkOrdinates(oraGeom, elemIndex, "LineString");
  	checkETYPE(etype, OraGeom.ETYPE.LINE, "LineString");
  	checkInterpretation(interpretation, OraGeom.INTERP.LINESTRING, "LineString");
	
    int start = coordIndex(oraGeom, elemIndex);
    int end = coordIndex(oraGeom, elemIndex + 1);
    LineString line = geometryFactory.createLineString(
        subSeq(coords, start, end));
    return line;
  }

    /**
     * Create Point as encoded.
     *
   * @param oraGeom SDO_GEOMETRY attributes being read
   * @param elemIndex the element being read
   * @param coords the coordinates of the entire geometry
     * @return Point
     */
    private Point readPoint(OraGeom oraGeom, int elemIndex, CoordinateSequence coords) {
      int etype = oraGeom.eType(elemIndex);
      int interpretation = oraGeom.interpretation(elemIndex);

  		checkOrdinates(oraGeom, elemIndex, "Point");
  		checkETYPE(etype,OraGeom.ETYPE.POINT, "Point");
  		checkInterpretation(interpretation, OraGeom.INTERP.POINT, "Point");
  
      int start = coordIndex(oraGeom, elemIndex);
      int end = coordIndex(oraGeom, elemIndex + 1);
      return createPoint(subSeq(coords,start,end));
    }

    private Point createPoint(CoordinateSequence coords)
    {
      return geometryFactory.createPoint(coords);
    }

    private static int coordIndex(OraGeom oraGeom, int elemIndex) 
    {
      int sOffset = oraGeom.startingOffset(elemIndex);
      int ordDim = oraGeom.ordDim();
      return (sOffset - 1) / ordDim;
    }
    
	/**
   * Extracts a subsequence of a CoordinateSequence.
   * If the entire sequence is requested, 
   * the original sequence is returned to avoid
   * creating a duplicate.
   * <p>
   * The subsequence runs from start (inclusive) to end (exclusive):
   *
   * @param coords coords to extract from
   * @param start starting offset
   * @param end upper bound of subsequence
   *
   * @return a CoordinateSequence
   */
    private CoordinateSequence subSeq(CoordinateSequence coords, int start, int end) {
      CoordinateSequenceFactory csFactory = geometryFactory.getCoordinateSequenceFactory();
      if ((start == 0) && (end == coords.size())) {
          return coords;
      }
      if (coords instanceof List) {
          List sublist = ((List) coords).subList(start, end);
          if (sublist instanceof CoordinateSequence) {
              return (CoordinateSequence) sublist;
          }
      }
      CoordinateList list = new CoordinateList(coords.toCoordinateArray());
      Coordinate[] array = new Coordinate[end - start];
      int index = 0;
      for (Iterator i = list.subList(start, end).iterator(); i.hasNext(); index++) {
          array[index] = (Coordinate) i.next();
      }
      return csFactory.create(array);
    }

    private static void checkETYPE(int eType, int val1, String geomType)
    {
    	checkETYPE(eType,val1, -1, -1, geomType);
    }
    
    private static void checkETYPE(int eType, int val1, int val2, String geomType)
    {
    	checkETYPE(eType,val1, val2, -1, geomType);
    }
    
    private static void checkETYPE(int eType, int val1, int val2, int val3, String geomType)
    {
    	if (eType == val1) return;
    	if (val2 >= 0 && eType == val2) return;
    	if (val3 >= 0 && eType == val3) return;
    	throw new IllegalArgumentException("SDO_ETYPE "+ eType +" is not supported when reading a " + geomType);
    }
    
    private static void checkInterpretation(int interpretation, int val1, String geomType) {
    	checkInterpretation(interpretation, val1, -1, geomType);
    }
    
    private static void checkInterpretation(int interpretation, int val1, int val2, String geomType) {
		if (interpretation == val1) return;
	   	if (val2 >= 0 && interpretation == val2) return;
	   	errorInterpretation(interpretation, geomType);
    }

  	private static void errorInterpretation(int interpretation, String geomType) {
  		throw new IllegalArgumentException("SDO_INTERPRETATION "+ interpretation +" is not supported when reading a " + geomType);
  	}

    private static void checkOrdinates(OraGeom oraGeom, int elemIndex, String geomType)
    {
      int startOffset = oraGeom.startingOffset(elemIndex);
      int ordLen = oraGeom.ordinateLen();
      checkOrdinates(oraGeom.elemInfo, elemIndex, startOffset, ordLen, geomType);
    }

    private static void checkOrdinates(int[] elemInfo, int elemIndex, int startOffset, int ordLen, String geomType)
    {
      if (startOffset > ordLen)
		    throw new IllegalArgumentException("STARTING_OFFSET " + startOffset + " inconsistent with ORDINATES length " + ordLen
		    		+ " (Element " + elemIndex + " in SDO_ELEM_INFO " + OraGeom.toStringElemInfo(elemInfo) + ")");
    }
    

}
