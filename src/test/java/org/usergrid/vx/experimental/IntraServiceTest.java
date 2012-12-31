package org.usergrid.vx.experimental;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cassandra.db.marshal.IntegerType;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.usergrid.vx.server.IntravertCassandraServer;
import org.usergrid.vx.server.IntravertDeamon;
import org.vertx.java.core.Vertx;

@RunWith(CassandraRunner.class)
@RequiresKeyspace(ksName = "myks")
@RequiresColumnFamily(ksName = "myks", cfName = "mycf")
public class IntraServiceTest {

  IntraService is = new IntraService();
  Vertx x = Vertx.newVertx();

  @DataLoader(dataset = "mydata.txt")
  @Test
	public void atest() throws CharacterCodingException{
		IntraReq req = new IntraReq();
		req.add( IntraOp.setKeyspaceOp("myks") ); //0
		req.add( IntraOp.setColumnFamilyOp("mycf") ); //1
		req.add( IntraOp.setAutotimestampOp() ); //2
		req.add( IntraOp.setOp("rowa", "col1", "7")); //3
		req.add( IntraOp.sliceOp("rowa", "col1", "z", 4)); //4
		req.add( IntraOp.getOp("rowa", "col1")); //5
		//create a rowkey "rowb" with a column "col2" and a value of the result of operation 7
		req.add( IntraOp.setOp("rowb", "col2", IntraOp.getResRefOp(5, "value"))); //6
		//Read this row back 
		req.add( IntraOp.getOp("rowb", "col2"));//7
		
		req.add( IntraOp.consistencyOp("ALL")); //8
		req.add( IntraOp.listKeyspacesOp()); //9
		req.add(IntraOp.listColumnFamilyOp("myks"));//10
		IntraRes res = new IntraRes();
		
		is.handleIntraReq(req, res, x);
		
		Assert.assertEquals (  "OK" , res.getOpsRes().get(0)  );
		Assert.assertEquals (  "OK" , res.getOpsRes().get(1)  );
		Assert.assertEquals (  "OK" , res.getOpsRes().get(2)  );
		Assert.assertEquals (  "OK" , res.getOpsRes().get(3)  );
		List<Map> x = (List<Map>) res.getOpsRes().get(4);
		Assert.assertEquals( "col1", ByteBufferUtil.string((ByteBuffer) x.get(0).get("name")) );
		Assert.assertEquals( "7", ByteBufferUtil.string((ByteBuffer) x.get(0).get("value")) );
		
		x = (List<Map>) res.getOpsRes().get(5);
		Assert.assertEquals( "7", ByteBufferUtil.string((ByteBuffer) x.get(0).get("value"))  );
		
		Assert.assertEquals( "OK" , res.getOpsRes().get(6)  );
		
		x = (List<Map>) res.getOpsRes().get(7);
		Assert.assertEquals( "7", ByteBufferUtil.string((ByteBuffer) x.get(0).get("value"))  );
		
		Assert.assertEquals( "OK" , res.getOpsRes().get(8)  );
		Assert.assertEquals( true , ((List<String>) res.getOpsRes().get(9)).contains("myks")  );
		Set s = new HashSet();
		s.add("mycf");
		Assert.assertEquals( s , res.getOpsRes().get(10)  );
		
	}
	
	@Test
  public void exceptionHandleTest() throws CharacterCodingException{
    IntraReq req = new IntraReq();
    req.add( IntraOp.createKsOp("makeksagain", 1)); //0
    req.add( IntraOp.createKsOp("makeksagain", 1)); //1
    req.add( IntraOp.createKsOp("makeksagain", 1)); //2
    IntraRes res = new IntraRes();
    is.handleIntraReq(req, res, x);
    Assert.assertEquals (  "OK" , res.getOpsRes().get(0)  );
    Assert.assertEquals( 1, res.getOpsRes().size() );
    Assert.assertNotNull( res.getException() );
    Assert.assertEquals( new Integer(1) , res.getExceptionId() );
  }
	
	 @Test
	  public void assumeTest() throws CharacterCodingException{
	    IntraReq req = new IntraReq();
	    req.add( IntraOp.setKeyspaceOp("assks") ); //0
	    req.add( IntraOp.createKsOp("assks", 1)); //1
	    req.add( IntraOp.createCfOp("asscf")); //2
	    req.add( IntraOp.setColumnFamilyOp("asscf") ); //3
	    req.add( IntraOp.setAutotimestampOp() ); //4
	    req.add( IntraOp.assumeOp("assks", "asscf", "value", "UTF-8"));//5
	    req.add( IntraOp.setOp("rowa", "col1", "wow")); //6
	    req.add( IntraOp.getOp("rowa", "col1")); //7
	    IntraRes res = new IntraRes();
	    is.handleIntraReq(req, res, x);
	    List<Map> x = (List<Map>) res.getOpsRes().get(7);
	    Assert.assertEquals( "wow",  x.get(0).get("value") );
	  }
	
	 @Test
	 public void filterTest() throws CharacterCodingException{
	   IntraReq req = new IntraReq();
     req.add( IntraOp.setKeyspaceOp("filterks") ); //0
     req.add( IntraOp.createKsOp("filterks", 1)); //1
     req.add( IntraOp.createCfOp("filtercf")); //2
     req.add( IntraOp.setColumnFamilyOp("filtercf") ); //3
     req.add( IntraOp.setAutotimestampOp() ); //4
     req.add( IntraOp.assumeOp("filterks", "filtercf", "value", "UTF-8"));//5
     req.add( IntraOp.setOp("rowa", "col1", "20")); //6
     req.add( IntraOp.setOp("rowa", "col2", "22")); //7
     req.add( IntraOp.createFilterOp("over21", "groovy", 
     		"public class Over21 implements org.usergrid.vx.experimental.Filter { \n"+
        " public Map filter(Map row){ \n" +
        "   if (Integer.parseInt( row.get(\"value\") ) >21){ \n"+
        "     return row; \n" +
        "   } else { return null; } \n" +
        " } \n" +
        "} \n"
     )); //8
     req.add( IntraOp.filterModeOp("over21", true)); //9
     req.add( IntraOp.sliceOp("rowa", "col1", "col3", 10)); //10
     IntraRes res = new IntraRes();
     is.handleIntraReq(req, res, x);
     System.out.println ( res.getException() );
     List<Map> results = (List<Map>) res.getOpsRes().get(10);
     Assert.assertEquals( "22", results.get(0).get("value") );
     Assert.assertEquals(1, results.size());
     
	 }
	 
	 @Test
   public void processorTest() throws CharacterCodingException{
     IntraReq req = new IntraReq();
     req.add( IntraOp.setKeyspaceOp("procks") ); //0
     req.add( IntraOp.createKsOp("procks", 1)); //1
     req.add( IntraOp.createCfOp("proccf")); //2
     req.add( IntraOp.setColumnFamilyOp("proccf") ); //3
     req.add( IntraOp.setAutotimestampOp() ); //4
     req.add( IntraOp.assumeOp("procks", "proccf", "value", "UTF-8"));//5
     req.add( IntraOp.setOp("rowa", "col1", "wow")); //6
     req.add( IntraOp.getOp("rowa", "col1")); //7
     req.add( IntraOp.createProcessorOp("capitalize", "groovy", 
         "public class Capitalize implements org.usergrid.vx.experimental.Processor { \n"+
         "  public List<Map> process(List<Map> input){" +
         "    List<Map> results = new ArrayList<HashMap>();"+
         "    for (Map row: input){" +
         "      Map newRow = new HashMap(); "+
         "      newRow.put(\"value\",row.get(\"value\").toString().toUpperCase());" +
         "      results.add(newRow); "+
         "    } \n" +
         "    return results;"+
         "  }"+
         "}\n"
     ));//8
     //TAKE THE RESULT OF STEP 7 AND APPLY THE PROCESSOR TO IT
     req.add( IntraOp.processOp("capitalize", new HashMap(), 7));//9
     IntraRes res = new IntraRes();
     is.handleIntraReq(req, res, x);
     List<Map> x = (List<Map>) res.getOpsRes().get(7);
     Assert.assertEquals( "wow",  x.get(0).get("value") );
     System.out.println(res.getException() );
     Assert.assertNull( res.getException() );
     x = (List<Map>) res.getOpsRes().get(9);
     Assert.assertEquals( "WOW",  x.get(0).get("value") );
   }
	 
	 
	 @Test
   public void intTest() throws CharacterCodingException{
     IntraReq req = new IntraReq();
     req.add( IntraOp.setKeyspaceOp("intks") ); //0
     req.add( IntraOp.createKsOp("intks", 1)); //1
     req.add( IntraOp.createCfOp("intcf")); //2
     req.add( IntraOp.setColumnFamilyOp("intcf") ); //3
     req.add( IntraOp.setAutotimestampOp() ); //4
     req.add( IntraOp.assumeOp("intks", "intcf", "value", "UTF-8"));//5
     req.add( IntraOp.assumeOp("intks", "intcf", "column", "int32"));//6
     req.add( IntraOp.setOp("rowa", 1, "wow")); //7
     req.add( IntraOp.getOp("rowa", 1)); //8
     
     IntraRes res = new IntraRes();
     is.handleIntraReq(req, res, x);
     List<Map> x = (List<Map>) res.getOpsRes().get(8);
     
     Assert.assertEquals( "wow",  x.get(0).get("value") );
     Assert.assertEquals( 1,  x.get(0).get("name") );
   }
	 
	 @Test
   public void compositeTest() throws CharacterCodingException{ 
     IntraReq req = new IntraReq();
     req.add( IntraOp.setKeyspaceOp("compks") ); //0
     req.add( IntraOp.createKsOp("compks", 1)); //1
     req.add( IntraOp.createCfOp("compcf")); //2
     req.add( IntraOp.setColumnFamilyOp("compcf") ); //3
     req.add( IntraOp.setAutotimestampOp() ); //4
     req.add( IntraOp.assumeOp("compks", "compcf", "value", "CompositeType(UTF-8,int32)"));//5
     req.add( IntraOp.assumeOp("compks", "compcf", "column", "int32"));//6
     req.add( IntraOp.setOp("rowa", 1, new Object[] {"yo",0, 2,0})); //7
     req.add( IntraOp.getOp("rowa", 1)); //8
      
     IntraRes res = new IntraRes();
     is.handleIntraReq(req, res, x);
     List<Map> x = (List<Map>) res.getOpsRes().get(8);
     Assert.assertEquals( 1,  x.get(0).get("name") );
     Assert.assertEquals( "yo",  ((Object [])x.get(0).get("value"))[0] );
     Assert.assertEquals( 2,  ((Object [])x.get(0).get("value"))[1] );
   }
	    
	 
	 @Test
   public void CqlTest() throws CharacterCodingException{ 
     IntraReq req = new IntraReq();
     req.add( IntraOp.setKeyspaceOp("cqlks") ); //0
     req.add( IntraOp.createKsOp("cqlks", 1)); //1
     req.add( IntraOp.createCfOp("cqlcf")); //2
     req.add( IntraOp.setColumnFamilyOp("cqlcf") ); //3
     req.add( IntraOp.setAutotimestampOp() ); //4
     req.add( IntraOp.assumeOp("cqlks", "cqlcf", "value", "int32"));//5
     req.add( IntraOp.assumeOp("cqlks", "cqlcf", "column", "int32"));//6
     req.add( IntraOp.setOp("rowa", 1, 2)); //7
     req.add( IntraOp.getOp("rowa", 1)); //8
     req.add( IntraOp.cqlQuery("select * from cqlcf", "3.0.0"));//9
      
     IntraRes res = new IntraRes();
     is.handleIntraReq(req, res, x);
     List<Map> x = (List<Map>) res.getOpsRes().get(8);
     Assert.assertEquals( 1,  x.get(0).get("name") );
     Assert.assertEquals( 2,  x.get(0).get("value") );
     x = (List<Map>) res.getOpsRes().get(9);
     Assert.assertEquals( 2, IntegerType.instance.compose((ByteBuffer)x.get(0).get("value")) );
     
   }

}
