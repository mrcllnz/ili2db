package ch.ehi.sqlgen.generator_impl.fgdb;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.sql.Connection;

import ch.ehi.basics.logging.EhiLogger;
import ch.ehi.basics.settings.Settings;
import ch.ehi.fgdb4j.jni.*;
import ch.ehi.ili2fgdb.jdbc.FgdbConnection;
import ch.ehi.sqlgen.DbUtility;
import ch.ehi.sqlgen.generator.Generator;
import ch.ehi.sqlgen.generator.SqlConfiguration;
import ch.ehi.sqlgen.repository.*;

public class GeneratorFgdb implements Generator {

	public static final String OBJECTOID = "OBJECTID";
	private FgdbConnection conn;
	private Geodatabase db;

	@Override
	public void visit1Begin() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit1End() throws IOException {
		// TODO Auto-generated method stub
		
	}

	private FieldDefs fieldv = null;
	private String geomFieldName=null;
	
	@Override
	public void visit1TableBegin(DbTable tab) throws IOException {
		if(!tableExists(db,tab.getName().getName())){
			fieldv = new FieldDefs();
		}else{
			fieldv=null;
		}
		geomFieldName=null;
	}

	public static boolean tableExists(Connection conn,DbTableName name) {
		return tableExists(((FgdbConnection) conn).getGeodatabase(),name.getName());
	}
	public static boolean tableExists(Geodatabase db,String name) {
		Table table=new Table();
		int ret=db.OpenTable(name, table);
		if(ret==0){
			db.CloseTable(table);
			return true;
		}
		if(ret!=-2147220655){
			throw new IllegalStateException("failed to test if table exists");
		}
		return false;
	}

	@Override
	public void visit1TableEnd(DbTable tab) throws IOException {
		if(fieldv!=null){
			Table table=new Table();
			// add OID field, so that table can be searched
			FieldDef field = new FieldDef();
			field.SetName(OBJECTOID);
			field.SetType(FieldType.fieldTypeOID);
			field.SetIsNullable(false);
			fieldv.add(field);
			db.CreateTable(tab.getName().getName(), fieldv, "", table);
			db.CloseTable(table);
		}else{
			// table already exists
			if(tab.isDeleteDataIfTableExists()){
				String delStmt="DELETE FROM "+tab.getName().getName();
				EhiLogger.traceBackendCmd(delStmt);
				EnumRows rows=new EnumRows();
				int err=db.ExecuteSQL(delStmt, false, rows);
				if(err!=0){
					throw new IllegalStateException("failed to delete data from "+tab.getName().getName());
				}
			}
		}
	}

	@Override
	public void visit2Begin() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit2End() throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit2TableBegin(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visit2TableEnd(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitColumn(DbTable tab, DbColumn column) throws IOException {
		// skip this table?
		if(fieldv==null){
			// ignore cols
			return;
		}
		//EhiLogger.debug("col "+column.getName());
		boolean isOid=false;
		FieldDef field = new FieldDef();
		if(column instanceof DbColBoolean){
			field.SetType(FieldType.fieldTypeSmallInteger);
		}else if(column instanceof DbColDateTime){
			field.SetType(FieldType.fieldTypeDate);
		}else if(column instanceof DbColDate){
			field.SetType(FieldType.fieldTypeDate);
		}else if(column instanceof DbColTime){
			field.SetType(FieldType.fieldTypeDate);
		}else if(column instanceof DbColDecimal){
			DbColDecimal col=(DbColDecimal)column;
			field.SetType(FieldType.fieldTypeDouble);
			field.SetLength(col.getSize());
			//field.SetPrecision(col.getPrecision());
		}else if(column instanceof DbColGeometry){
			DbColGeometry col=(DbColGeometry)column;
			if(geomFieldName==null){
				SpatialReferenceInfo srsInfo = new SpatialReferenceInfo();
				Integer srsId=getSrsId(col.getSrsAuth(), col.getSrsId(),srsInfo);
				if(srsId==null){
					throw new IllegalArgumentException("Unknown CRS "+col.getSrsAuth()+":"+col.getSrsId());
				}
				  SpatialReference spatialReference=new SpatialReference();
				  spatialReference.SetSpatialReferenceText (srsInfo.getSrtext());
				  spatialReference.SetSpatialReferenceID(srsInfo.getAuth_srid()); 
				  //spatialReference.SetXYFalseOrigin(-16987000, -8615900);
				  //spatialReference.SetXYResolution(.0001);
				  //spatialReference.SetXYTolerance(.001);
				GeometryDef geomDef = new GeometryDef();
				if(col.getType()==DbColGeometry.POINT){
					geomDef.SetGeometryType(GeometryType.geometryPoint);
				}else if(col.getType()==DbColGeometry.COMPOUNDCURVE || col.getType()==DbColGeometry.CIRCULARSTRING || col.getType()==DbColGeometry.LINESTRING){
					geomDef.SetGeometryType(GeometryType.geometryPolyline);
				}else if(col.getType()==DbColGeometry.CURVEPOLYGON || col.getType()==DbColGeometry.POLYGON){
					geomDef.SetGeometryType(GeometryType.geometryPolygon);
				}
				geomDef.SetSpatialReference(spatialReference);
				geomDef.SetHasZ(false); //Set to true if the feature class is to be Z enabled. Defaults to FALSE.
				geomDef.SetHasM(false); //Set to true if the feature class is to be M enabled. Defaults to FALSE.
				field.SetType(FieldType.fieldTypeGeometry);
				field.SetGeometryDef(geomDef);
				geomFieldName=col.getName();
			}
		}else if(column instanceof DbColId){
			DbColId col=(DbColId)column;
			if(col.isPrimaryKey()){
				//field.SetType(FieldType.fieldTypeOID); // not used, because of auto-generated values by the FGDB API
				field.SetType(FieldType.fieldTypeInteger);
				isOid=true;
			}else{
				field.SetType(FieldType.fieldTypeInteger);
			}
		}else if(column instanceof DbColNumber){
			DbColNumber col=(DbColNumber)column;
			field.SetType(FieldType.fieldTypeInteger);
			field.SetLength(col.getSize());
		}else if(column instanceof DbColVarchar){
			int colsize=((DbColVarchar)column).getSize();
			if(colsize==DbColVarchar.UNLIMITED){
				field.SetLength(2147483646);
			}else{
				field.SetLength(colsize);
			}
			field.SetType(FieldType.fieldTypeString);
		}else if(column instanceof DbColUuid){
			//field.SetType(FieldType.fieldTypeGlobalID);
			//field.SetType(FieldType.fieldTypeGUID);
			field.SetType(FieldType.fieldTypeString);
			field.SetLength(36);
		}else if(column instanceof DbColBlob){
			field.SetType(FieldType.fieldTypeBlob);
		}else if(column instanceof DbColXml){
			field.SetType(FieldType.fieldTypeString);
			field.SetLength(2147483646);
			//field.SetType(FieldType.fieldTypeXML);
		}else{
			field.SetLength(20);
			field.SetType(FieldType.fieldTypeString);
		}
		if(!isOid){
			field.SetIsNullable(!column.isNotNull());
		}
		field.SetName(column.getName());
		fieldv.add(field);
	}

	public static Integer getSrsId(String srsAuth, String srsId) {
		SpatialReferenceInfo srsInfo=new SpatialReferenceInfo();
		return getSrsId(srsAuth, srsId, srsInfo);
	}
	public static Integer getSrsId(String srsAuth, String srsId, SpatialReferenceInfo srsInfo) {
		Integer esriId=null;
		try {
			esriId = Integer.parseInt(srsId);
		} catch (NumberFormatException e) {
			return null;
		}
		if(!fgbd4j.FindSpatialReferenceBySRID(esriId,srsInfo)){
			return null;
		}
		if(!srsInfo.getAuth_name().equals(srsAuth)){
			return null;
		}
		return esriId;
	}

	@Override
	public void visitConstraint(DbConstraint arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitEnumEle(DbEnumEle arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitIndex(DbIndex arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitSchemaBegin(Settings config, DbSchema arg1)
			throws IOException {
		conn=(FgdbConnection)config.getTransientObject(SqlConfiguration.JDBC_CONNECTION);
		if(conn==null){
			throw new IllegalArgumentException("config.getConnection()==null");
		}
		db = conn.getGeodatabase();
	}

	@Override
	public void visitSchemaEnd(DbSchema arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableBeginColumn(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableBeginConstraint(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableBeginEnumEle(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableBeginIndex(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableEndColumn(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableEndConstraint(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableEndEnumEle(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void visitTableEndIndex(DbTable arg0) throws IOException {
		// TODO Auto-generated method stub
		
	}

}