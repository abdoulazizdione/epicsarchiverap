/*******************************************************************************
 * Copyright (c) 2011 The Board of Trustees of the Leland Stanford Junior University
 * as Operator of the SLAC National Accelerator Laboratory.
 * Copyright (c) 2011 Brookhaven National Laboratory.
 * EPICS archiver appliance is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 *******************************************************************************/
package org.epics.archiverappliance.etl;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.epics.archiverappliance.common.BasicContext;
import org.epics.archiverappliance.common.TimeUtils;
import org.epics.archiverappliance.common.YearSecondTimestamp;
import org.epics.archiverappliance.config.ArchDBRTypes;
import org.epics.archiverappliance.config.ConfigServiceForTests;
import org.epics.archiverappliance.config.PVTypeInfo;
import org.epics.archiverappliance.config.StoragePluginURLParser;
import org.epics.archiverappliance.config.exception.AlreadyRegisteredException;
import org.epics.archiverappliance.data.ScalarValue;
import org.epics.archiverappliance.engine.membuf.ArrayListEventStream;
import org.epics.archiverappliance.retrieval.RemotableEventStreamDesc;
import org.epics.archiverappliance.utils.simulation.SimulationEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.slac.archiverappliance.PlainPB.PlainPBStoragePlugin;
/**
 *  test for consolidate all pb files from short term storage to medium term storage
 * @author Luofeng Li
 *
 */
public class ConsolidateETLJobsForOnePVTest2 extends TestCase{

        private static Logger logger = Logger.getLogger(ConsolidateETLJobsForOnePVTest2.class.getName());
        String rootFolderName = ConfigServiceForTests.getDefaultPBTestFolder() + "/" + "ConsolidateETLJobsForOnePVTest";
        String shortTermFolderName=rootFolderName+"/shortTerm";
        String mediumTermFolderName=rootFolderName+"/mediumTerm";
        String longTermFolderName=rootFolderName+"/longTerm";
        String pvName = "ArchUnitTest" + "ConsolidateETLJobsForOnePVTest";
        PlainPBStoragePlugin storageplugin1;
        PlainPBStoragePlugin storageplugin2;
        PlainPBStoragePlugin storageplugin3;
        short currentYear = TimeUtils.getCurrentYear();
        ArchDBRTypes type = ArchDBRTypes.DBR_SCALAR_DOUBLE;
        private  ConfigServiceForTests configService;
        
        @Before
        public void setUp() throws Exception {
        	configService = new ConfigServiceForTests(new File("./bin"));
                if(new File(rootFolderName).exists()) {
                        FileUtils.deleteDirectory(new File(rootFolderName));
                }

                storageplugin1 = (PlainPBStoragePlugin) StoragePluginURLParser.parseStoragePlugin("pb://localhost?name=STS&rootFolder=" + shortTermFolderName + "/&partitionGranularity=PARTITION_HOUR", configService);
                storageplugin2 = (PlainPBStoragePlugin) StoragePluginURLParser.parseStoragePlugin("pb://localhost?name=MTS&rootFolder=" + mediumTermFolderName + "/&partitionGranularity=PARTITION_HOUR&hold=5&gather=3", configService);
                storageplugin3 = (PlainPBStoragePlugin) StoragePluginURLParser.parseStoragePlugin("pb://localhost?name=LTS&rootFolder=" + longTermFolderName + "/&partitionGranularity=PARTITION_HOUR&compress=ZIP_PER_PV", configService);
        }

        @After
        public void tearDown() throws Exception {
        	// FileUtils.deleteDirectory(new File(rootFolderName));
        	configService.shutdownNow();
        }
        @Test
        public void testAll(){
        	try {
        		Consolidate();
        	} catch (AlreadyRegisteredException | IOException | InterruptedException e) {
        		logger.error(e);
        	}
        }
        
       
        private void Consolidate() throws AlreadyRegisteredException, IOException, InterruptedException{
                 PVTypeInfo typeInfo = new PVTypeInfo(pvName, ArchDBRTypes.DBR_SCALAR_DOUBLE, true, 1);
                String[] dataStores = new String[] { storageplugin1.getURLRepresentation(), storageplugin2.getURLRepresentation(),storageplugin3.getURLRepresentation() }; 
                typeInfo.setDataStores(dataStores);
                configService.updateTypeInfoForPV(pvName, typeInfo);
                configService.registerPVToAppliance(pvName, configService.getMyApplianceInfo());
                configService.getETLLookup().manualControlForUnitTests();
                //generate datas of 10 days PB file 2012_01_01.pb  to 2012_01_10.pb
                int dayCount=10;
                for(int day = 0; day < dayCount; day++) {
                                logger.debug("Generating data for day " + 1);
                                int startofdayinseconds = day*24*60*60;
                                int runsperday = 12;
                                int eventsperrun = 24*60*60/runsperday;
                                for(int currentrun = 0; currentrun < runsperday; currentrun++) {
                                        try(BasicContext context = new BasicContext()) {
                                                logger.debug("Generating data for run " + currentrun);
                                                
                                                YearSecondTimestamp yts = new YearSecondTimestamp(currentYear, (day+1)*24*60*60, 0);
                                                Timestamp etlTime = TimeUtils.convertFromYearSecondTimestamp(yts);
                                                logger.debug("Running ETL as if it were " + TimeUtils.convertToHumanReadableString(etlTime));
                                                ETLExecutor.runETLs(configService, etlTime);
                                                ArrayListEventStream testData = new ArrayListEventStream(eventsperrun, new RemotableEventStreamDesc(type, pvName, currentYear));
                                                for(int secondsinrun = 0; secondsinrun < eventsperrun; secondsinrun++) {
                                                        testData.add(new SimulationEvent(startofdayinseconds + currentrun*eventsperrun + secondsinrun, currentYear, type, new ScalarValue<Double>((double) secondsinrun)));
                                                }
                                                storageplugin1.appendData(context, pvName, testData);
                                        }
                                        // Sleep for a couple of seconds so that the modification times are different.
                                        Thread.sleep(2*1000);
                                }
                }// end for 
                
                
                File shortTermFIle=new File(shortTermFolderName);
                File mediumTermFIle=new File(mediumTermFolderName);
                //File longTermFIle=new File(longTermFolderName);
                
                String[]filesShortTerm= shortTermFIle.list();
                String[]filesMediumTerm= mediumTermFIle.list();
                assertTrue("there should be PB files int short term storage but there is no ",filesShortTerm.length!=0);
                assertTrue("there should be PB files int medium term storage but there is no ",filesMediumTerm.length!=0);
               //ArchUnitTestConsolidateETLJobsForOnePVTest:_pb.zip
                File zipFileOflongTermFile=new File(longTermFolderName+"/"+pvName+":_pb.zip");
                assertTrue(longTermFolderName+"/"+pvName+":_pb.zip shoule exist but it doesn't",zipFileOflongTermFile.exists());
               
                // consolidate
                //String storageName="LTS";
                //pbraw://MTS
                String storageName="MTS";
                Timestamp oneYearLaterTimeStamp=TimeUtils.convertFromEpochSeconds(TimeUtils.getCurrentEpochSeconds()+365*24*60*60, 0);
                ETLExecutor.runPvETLsBeforeOneStorage(configService, oneYearLaterTimeStamp, pvName, storageName);
                // make sure there are no pb files in short term storage , medium term storage and all files in long term storage
                Thread.sleep(4000);
               String[]filesShortTerm2= shortTermFIle.list();
               String[]filesMediumTerm2= mediumTermFIle.list();
               assertTrue("there should be no files int short term storage but there are still "+filesShortTerm2.length+"PB files",filesShortTerm2.length==0);
             
               // check the file of short term storage come into medium storage
               for(String fileShortTermTemp1:filesShortTerm){
                    boolean exits=false;
                    for(String filesMediumTermTemp1:filesMediumTerm2){
                            if(fileShortTermTemp1.equals(filesMediumTermTemp1)){
                                    exits=true;
                                    break;
                            }
                    }
                    
                    assertTrue("the file "+fileShortTermTemp1+" doesn't exist in "+mediumTermFolderName,exits);
               }
               
               
               for(String filesMediumTermTemp3:filesMediumTerm){
                    boolean exits=false;
                    for(String filesMediumTermTemp2:filesMediumTerm2){
                            if(filesMediumTermTemp3.equals(filesMediumTermTemp2)){
                                    exits=true;
                                    break;
                            }
                    }
                    
                    assertTrue("the file "+filesMediumTermTemp3+" doesn't exist in "+mediumTermFolderName,exits);
               }
               
         

        }
        
}