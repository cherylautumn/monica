package org.imeds.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.imeds.feature.screening.feature;
import org.imeds.feature.screening.measureScore;
import org.imeds.util.writeException;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.RList;
import org.rosuda.JRI.RVector;
import org.rosuda.JRI.Rengine;

public class ImedR {

	private static Logger logger = Logger.getLogger(ImedR.class);
	protected static Rengine re = null;
	public static void connR() throws Exception {
	   		re = new Rengine(new String[] { "--vanilla" }, false, null);
			logger.info("Rengine created, waiting for R");
	 
	        // the engine creates R is a new thread, so we should wait until it's
	        // ready
	        if (!re.waitForR()) {
	        	logger.info("Cannot load R");
	            return;
	        }
	        re.eval("library(survival)");
	        re.eval("library(KMsurv)");
	        re.eval("library(risksetROC)");
	        re.eval("library(MASS)");
	        
	  }
	public static void closeR() {
		   re.end();
	       re=null;
	} 

	public static void getCoxOutliler(String rFileName,ArrayList<Double> coxResidual,ArrayList<Double> coxPredict) throws Exception{
     
        //ArrayList<Double> value = new ArrayList<Double>();
        try {
            synchronized (ImedR.class) {
     	        re.eval("coxData<-read.csv(\""+rFileName+"\", header=T, sep=\",\") ");
		        //re.eval("coxData");
		        String coxph="coxfit<-coxph(Surv(Period,Censored)~D0+D2+D3+D4+D5+D6+D7+D8+D9+D10+D11+D12+D13+D14+D15+D16+D17, data=coxData)";
		        re.eval(coxph);
		        String deviance="devi<-resid(coxfit,type='deviance')";
		        
		        System.out.println(deviance);
		        re.eval(deviance);
		        REXP devianceResi=re.eval("devi");        
		        double[] devianceList=devianceResi.asDoubleArray();
		        
		        REXP hazard=re.eval("coxfit$linear.predictors");
		        
		        double[] hazardList =hazard.asDoubleArray();
		       
		        
		        System.out.println(devianceList.length+" / "+hazardList.length);
		        for (int i=0;i<devianceList.length;i++){
		        	coxResidual.add(devianceList[i]);
		        	coxPredict.add(hazardList[i]);
		
		        }
		        
            }
        }catch (Exception ex) {
            throw ex;
        }       
	}

	public static void loadRisksetAUCdata(String rFileName){
		String coxData = "coxData<-read.csv(\""+rFileName+"\", header=T, sep=\",\") ";
	     re.eval(coxData);
	}
	public static ArrayList<measureScore> getrisksetAUC(String rFileName,ArrayList<feature> result, Integer topK, Integer incrementN, Integer coxiter,Integer stop, ArrayList<String> yTitle) throws Exception{

//        fit1=coxph(Surv(Period,Failed)~D3+D12+D13+D1+D33+D19+D21+D16+D20+D15+D4+D55+D46+D34+D49+D9+D23+D18+D6+D14+D53+D51+D27+D31+D10+D43+D54+D29+D2+D57+D24+D52+D32+D26+D45+D28+D48+D8+D0+D41+D50+D56+D38+D11+D42+D40+D39+D35+D25+D17+D5+D44+D37+D22+D7+D30+D47+D36, data=Rossi2,control = coxph.control(iter.max = 20000))
		ArrayList<measureScore> mscore = new ArrayList<measureScore>();
        try {
            synchronized (ImedR.class) {
            	System.out.println(rFileName);
     	        String coxData = "coxData<-read.csv(\""+rFileName+"\", header=T, sep=\",\") ";

//     	        re.eval(coxData);

		        String initFeature="";
		        for(int i=0;i<topK;i++){
		        	initFeature=initFeature+"D"+result.get(i).getId()+"+";
		        }
		        int lastid=topK;
		        int lock=0;
		        initFeature = initFeature.substring(0, initFeature.lastIndexOf("+"));
		        
		        while(true){
//	     	        System.out.println(initFeature);

			        String coxph="coxfit<-coxph(Surv("+yTitle.get(0)+","+yTitle.get(1)+")~"+initFeature+", data=coxData,control = coxph.control(iter.max = "+coxiter+"))";

			        re.eval(coxph);
			        
			        String etaStr = "eta<-coxfit$linear.predictor";
			        REXP eta=re.eval(etaStr);
//			        double[] etaList=eta.asDoubleArray();
			        
			        String raucStr ="rauc<-risksetAUC(Stime=coxData$"+yTitle.get(0)+", status=coxData$"+yTitle.get(1)+", marker=eta, method=\"Cox\", tmax= max(coxData$"+yTitle.get(0)+"),plot=FALSE)";			       
			      
			        re.eval(raucStr);
	
			        re.eval("cindex<-rauc$Cindex");
			        REXP Cindex=re.eval("cindex");
			        double cidx=Cindex.asDouble();

			        System.out.println(topK+"/"+cidx);
			        mscore.add(new measureScore(topK, cidx, coxData+"; "+coxph+"; "+etaStr+"; "+raucStr));
			        
			        topK=topK+incrementN;
			        if(topK >= stop){
			        	
//			        	topK= result.size();
			        	topK= stop;

			        	lock++;
			        	if(lock>1) break;
			        }
			        
			        for(int i=lastid;i<topK;i++){
			        	initFeature=initFeature+"+D"+result.get(i).getId();
			        }
			        lastid=topK;
		        } 
//		        System.out.println(rFileName+" "+Cindex.asDouble());
//		                dev.copy(jpeg,filename="/Users/cheryl/DevWorkSpace/demo/data/IMEDS/CgstHfComorbidDS/SurvivalSeqDS/seqDsmc/0.05.jpg");
//		                dev.off ();
		        return mscore;
            }
        }catch (Exception ex) {
            throw ex;
        }       
	}
	public static HashMap<Integer, Double> getSurvFunc(String rFileName) throws Exception{
	  HashMap<Integer, Double> survFunc=new HashMap<Integer, Double>();
      try {
          synchronized (ImedR.class) {
          	
   	        String coxData = "survData<-read.csv(\""+rFileName+"\", header=T, sep=\",\") ";   	        
   	        String SurvObj = "SurvObj<-Surv(survData$Period, survData$Failed == 1)";
   	        String mfit= "mfit <- survfit(SurvObj~1)";
   	   		
   	        re.eval(coxData);
   	        re.eval(SurvObj);
   	        re.eval(mfit);
   	        re.eval("st<-data.frame(mfit$time, mfit$surv) ");
	        REXP st=re.eval("st$mfit.time");
	        int[] time=st.asIntArray();
	       
	        st=re.eval("st$mfit.surv");
	        double[] surv=st.asDoubleArray();
	       
	        for(int i=0;i<time.length;i++){
	        	survFunc.put(time[i], surv[i]);
	        }
	        return survFunc;
          }
      }catch (Exception ex) {
          throw ex;
      }       
	}
}
