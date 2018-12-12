import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;

public class Hospital {
	static int                     nextID 		= 0;// to give each an ID
	private int                    ID 			= 0;
	private int                    level 		= 0; // 1=primary, ..., 4=higher level of hospitals in Korea
	private double                 longitude 	= 0.0;
	private double                 latitude 	= 0.0;
	private int                    regionID 	= -999;
	private double                 dayVaccinationStarted = 0;
	private boolean                indexHosp = false;
	
	// 0=Gangwon, 1=Gyeonggi, 2=Gyeongnam,
	// 3=Gyeongbuk, 4=Gwangju, 5=Daegu,
	// 6=Daejeon, 7=Busan, 8=Seoul,
	// 9=Sejong City, 10=Ulsan, 11=Incheon, 12=Jeonnam, 13=Jeonbuk, 
	// 14=Jeju, 15=Chungnam, 16=Chungbuk
//	private int                            popSize 		= 0;
	private ArrayList<Agent> 			   infectious 				= new ArrayList<Agent> (); //I
	private ArrayList<Agent> 			   susceptibles 			= new ArrayList<Agent> (); //S
	private ArrayList<Agent> 			   exposeds 				= new ArrayList<Agent> (); //E
	private ArrayList<Agent> 			   isolateds 				= new ArrayList<Agent> (); //J
	private ArrayList<Agent> 			   removeds 				= new ArrayList<Agent> (); //R
	private ArrayList<Agent> 			   isolatedRemoveds 		= new ArrayList<Agent> (); //JR

	private ArrayList<Agent> 			   vaccinatedSusceptibles 	= new ArrayList<Agent> (); //VS
	private ArrayList<Agent> 			   vaccinatedExposeds 		= new ArrayList<Agent> (); //VE
	private ArrayList<Agent> 			   vaccinatedProtecteds 	= new ArrayList<Agent> (); //VP
	
	private ArrayList<Agent> 			   quarantinedSusceptibles 	= new ArrayList<Agent> (); //QS
	private ArrayList<Agent> 			   quarantinedExposeds 		= new ArrayList<Agent> (); //QE
	private ArrayList<Agent> 			   quarantinedVaccinatedSusceptibles 	= new ArrayList<Agent> (); //QVS
	private ArrayList<Agent> 			   quarantinedVaccinatedExposeds 		= new ArrayList<Agent> (); //QVE
	private ArrayList<Agent> 			   quarantinedVaccinatedProtecteds 		= new ArrayList<Agent> (); //QVP
	
	
	
	private ArrayList<Agent> 			   vaccineReceived 		= new ArrayList<Agent> (); // to track the total number of vaccine doses
	
	
	//because only effective vaccine recipients are moved to the vacinated list, this can cause 
	//overestimation of the cumul vaccine dose
	//So remove vaccine Recipient from the eligible population 
	static Parameters pars = new Parameters ();
	static Utility util = new Utility ();

	/////////////////////////////////////////////////////////////////////////////////////////////
	// constructors
	// 
	public Hospital(){
		ID = nextID ++;
	}
	public Hospital( int N ){
		ID = nextID ++;
		ArrayList<Agent> list = this.getSusceptibles();
		for( int i = 0; i < N; ++i ) {
			Agent a = new Agent( "S" );
			a.getVisitedHospitals().add( this );
			list.add( a );
			a.setHospital( this );
		}
		if( pars.isPreEmptiveVaccination() ) {
			double fracHCW = pars.getFracHCW();
			double vaccEff = pars.getVaccCoverage();
			ArrayList<Agent> susc = this.getSusceptibles();
			ArrayList<Agent> agentProtected = new ArrayList<Agent>();
			for( Agent a : susc ) {
				if( Model.unifFromZeroToOne.sample() < fracHCW * vaccEff ) {
					a.setInfectionStatus( "VP" );
					agentProtected.add( a );
				}
			}
			this.getVaccinatedProtecteds().addAll( agentProtected );
			susc.removeAll( agentProtected );
			agentProtected.clear();;
		}
	}
	
	public Hospital( int n, String s ){
		ID = nextID ++;
		ArrayList<Agent> list = new ArrayList<Agent>();
		if( s.equals("S") ) {
			list = this.getSusceptibles();
		}
		else if( s.equals("E") ) {
			list = this.getExposeds();
		}
		else if( s.equals("I") ) {
			list = this.getInfectious();
		}
		else if( s.equals("J") ) {
			list = this.getIsolateds();
		}
		for( int i = 0; i < n; ++i ) {
			Agent a = new Agent();
			a.setInfectionStatus( s );
			list.add( a );
			a.setHospital( this );
			a.getVisitedHospitals().add( this );
		}
		
		if( pars.isPreEmptiveVaccination() ) {
			double fracHCW = pars.getFracHCW();
			double vaccEff = pars.getVaccCoverage();
			ArrayList<Agent> susc = this.getSusceptibles();
			ArrayList<Agent> agentProtected = new ArrayList<Agent>();
			for( Agent a : susc ) {
				if( Model.unifFromZeroToOne.sample() < fracHCW * vaccEff ) {
					a.setInfectionStatus( "VP" );
					agentProtected.add( a );
				}
			}
			this.getVaccinatedProtecteds().addAll( agentProtected );
			susc.removeAll( agentProtected );
			agentProtected.clear();
		}
	}
				

	

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// transmission( Parameters pars, double beta, double shape )
	//
	public void transmission( Parameters pars, double beta, double shape ) {
		ArrayList<Agent> agentToMoveToExposed = new ArrayList<Agent> ();
		ArrayList<Agent> agentToMoveToVaccinatedExposed = new ArrayList<Agent> ();
		ArrayList<Agent> infectious = this.getInfectious();

		ArrayList<Agent> totalSusc = new ArrayList<Agent>();
		totalSusc.addAll( this.getSusceptibles() );
		totalSusc.addAll( this.getVaccinatedSusceptibles() );
		
		int numSusc = totalSusc.size();
		int popSize = this.getPopulationSize();
		int newInfections = 0;

		double factor = pars.getFactorHighRiskTransmissibility();
		boolean transmissionOccurred = false;
		for( Agent a : infectious ) {
			if( pars.getDebug() > 2 )
				System.out.printf( "tick = %.1f, transmission begin .. infectious id = %d\n", Step.currentDay, a.getID() );
			double fracSusc = (double) numSusc / popSize; //updated for each infectious individual
			if( fracSusc > 0 ) {
			// assuming frequency-dependent transmission leading to mean decreasing proportionally to the fraction of susceptible
				PoissonDistribution pois = new PoissonDistribution( Model.RNG, beta * fracSusc, 1E-12, 10000000 );
				if( a.isHighInfectivity() && a.isInvader() ) { // we may just need to check invader status as all invaders have to be high-risk
					double scale = beta * fracSusc * factor / shape; // scale for the gamma distribution is by mean divided by shape
					GammaDistribution gamma = new GammaDistribution( Model.RNG, shape, scale );
					double randomMu = gamma.sample();
					if( randomMu <= 0 ) {
						randomMu = Double.MIN_VALUE;
					}
					pois = new PoissonDistribution( Model.RNG, randomMu, 1E-12, 10000000 );
				}
				newInfections = pois.sample();
				if( newInfections > numSusc ) {
					newInfections = numSusc;
				}
				numSusc = numSusc - newInfections;
				if( newInfections > 0 ) {
					ArrayList<Agent> offspring = new ArrayList<Agent>();
					for( int i = 0; i < newInfections; ++i ) {
						Agent b = totalSusc.get( i );
						offspring.add( b );
						b.setInfectedHospitalID( this.getID() );
						if( b.getInfectionStatus().equals("S") ) {
							agentToMoveToExposed.add( b );
						} else if( b.getInfectionStatus().equals("VS") ) {
							agentToMoveToVaccinatedExposed.add( b );
						}
						
						if( pars.getDebug() > 2 )
							System.out.println( "transmission occurred .. infector id =" + a.getID() + ",  infectee id = " + b.getID() );
					}
					totalSusc.removeAll( offspring );
					a.infect( pars, offspring );
					if( !transmissionOccurred ) {
						transmissionOccurred = true;
					}
				}
			}
		}
		if( transmissionOccurred ) {
			Model.hospitalsTransmissionOccurred.add( this );
		}
		this.getSusceptibles().removeAll( agentToMoveToExposed );
		this.getExposeds().addAll( agentToMoveToExposed );
		this.getVaccinatedSusceptibles().removeAll( agentToMoveToVaccinatedExposed );
		this.getVaccinatedExposeds().addAll( agentToMoveToVaccinatedExposed );

	}		
	
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// transmission( Parameters pars )
	//
	public void infectiousnessDevelopment( Parameters pars ) {
		ArrayList<Agent> agentsToBecomeInfectious = new ArrayList<Agent> ();
		ArrayList<Agent> agentsToBeIsolated = new ArrayList<Agent> (); // quarantined exposeds are isolated as soon as they develop symptoms
		
		ArrayList<Agent> E = this.getExposeds();
		ArrayList<Agent> VE = this.getVaccinatedExposeds(); // vaccinated people may become infectious
		ArrayList<Agent> QE = this.getQuarantinedExposeds();
		ArrayList<Agent> QVE = this.getQuarantinedVaccinatedExposeds();
		ArrayList<Agent> totalExposeds = new ArrayList<Agent> ();
		totalExposeds.addAll( E );
		totalExposeds.addAll( VE );
		totalExposeds.addAll( QE );
		totalExposeds.addAll( QVE );
		for( Agent a : totalExposeds ) {
			String infStatus = a.getInfectionStatus();
			if( ! ( infStatus.equals( "E" ) || infStatus.equals( "VE" ) ||  infStatus.equals( "QE" ) ||  infStatus.equals( "QVE" ) ) ){
				System.err.println( "infectiousnessDevelopment: check the infection state of the input agents -- " + infStatus );
			}
			else {
				if( a.getDurationOfIncubation() <= a.getDaySinceInfection() ) {
					if( infStatus.equals( "QE" ) || infStatus.equals( "QVE" ) ) {
						a.becomeInfectious( pars );
						a.beIsolated( pars );// quarantined individuals are isolated as soon as they develop symptoms
						agentsToBeIsolated.add( a );
					} else {
						a.becomeInfectious( pars );
						agentsToBecomeInfectious.add( a );
					}
				}
			}
		}
		E.removeAll( agentsToBecomeInfectious );
		VE.removeAll( agentsToBecomeInfectious );
		QE.removeAll( agentsToBeIsolated );
		QVE.removeAll( agentsToBeIsolated );
		
		this.getInfectious().addAll( agentsToBecomeInfectious ); // vaccinated & infected are not different from unvaccinated and infected
		this.getIsolateds().addAll( agentsToBeIsolated );
		
	}		
	
	
		
	
	/////////////////////////////////////////////////////////////////////////////////
	// isolation( Parameter pars )
	// agents get isolated some day after becoming symptomatic: I -> J
	protected void isolation( Parameters pars, double rate, double maxTimeFromSymptomOnsetToIsolation  ){
		ArrayList<Agent> agentIsolated = new ArrayList<Agent> ();
		for( Agent a : this.getInfectious() ) {
			if( !a.getInfectionStatus().equals( "I" ) ){
				System.err.println( "Hospital.isolation: infection status have to be I, the current state is " + a.getInfectionStatus() );
			}
			else{
				if( !a.isIsolated() && ( ( Model.unifFromZeroToOne.sample() < rate ) ||
						( a.getDaySinceSymptomOnset() >= maxTimeFromSymptomOnsetToIsolation ) ) ){
					a.beIsolated( pars );
					agentIsolated.add( a );
				}
			}
		}
		if( agentIsolated.size() > 0 ) {
			this.getInfectious().removeAll( agentIsolated );
			this.getIsolateds().addAll( agentIsolated );
			
			if( !Model.hospitalsCaseIsolated.contains( this ) ) {
				Model.hospitalsCaseIsolated.add( this );
			}
		}
	}
	

	
	
	
	/////////////////////////////////////////////////////////////////////////////////
	// isolation( Parameter pars )
	// agents get isolated some day after becoming symptomatic: I -> J
	protected void removal( Parameters pars ){
		ArrayList<Agent> selectedForRecovery = new ArrayList<Agent> ();
		ArrayList<Agent> selectedForRecoveryIsolated = new ArrayList<Agent> ();
		
		ArrayList<Agent> subjects = this.getInfectious();
		subjects.addAll( this.getIsolateds() );
		for( Agent a : subjects ) {
			String status = a.getInfectionStatus();
			if( ! (status.equals( "I" ) ||status.equals( "J" )) ){
				System.err.println( "Hospital.removal: Input agents need to be in the I or J state!, the current state is " + a.getInfectionStatus() );
			}
			else if( status.equals( "I" ) && ( a.getDurationOfInfectiousness() <= a.getDaySinceSymptomOnset() ) ){
					a.setInfectionStatus( "R" );
					selectedForRecovery.add( a );
			}
			else if( status.equals( "J" ) && ( a.getDurationOfInfectiousness() <= a.getDaySinceSymptomOnset() ) ){
					a.setInfectionStatus( "JR" );
					selectedForRecoveryIsolated.add( a );
			}
		}
		
		this.getInfectious().removeAll( selectedForRecovery );
		this.getIsolateds().removeAll( selectedForRecoveryIsolated );
		this.getRemoveds().addAll( selectedForRecovery );
		this.getIsolatedRemoveds().addAll( selectedForRecoveryIsolated );
	}
	

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// quarantine( Parameters pars, double rate, double maxTimeFromSymptomOnsetToIsolation, Hospital hosp ) {
	// quarantine exposed and susceptible 
	// because quarantine is not applied to the vaccinated susceptibles, who are still susceptible to infection, 
	// vaccination may cause increase in the incidence
	public void quarantine( Parameters pars, double rate, double maxTimeFromSymptomOnsetToIsolation ) {
		ArrayList<Agent> quarantined = new ArrayList<Agent> ();
		for( Agent a : this.getExposeds() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getExposeds().removeAll( quarantined );
		this.getQuarantinedExposeds().addAll( quarantined );
		
		quarantined.clear();
		for( Agent a : this.getSusceptibles() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getSusceptibles().removeAll( quarantined );//removed from infection transmission
		this.getQuarantinedSusceptibles().addAll( quarantined );
		
		quarantined.clear();
		
		for( Agent a : this.getVaccinatedSusceptibles() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getVaccinatedSusceptibles().removeAll( quarantined ); //removed from infection transmission
		this.getQuarantinedVaccinatedSusceptibles().addAll( quarantined );
		
		quarantined.clear();
		for( Agent a : this.getVaccinatedExposeds() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getVaccinatedExposeds().removeAll( quarantined ); //removed from infection transmission
		this.getQuarantinedVaccinatedExposeds().addAll( quarantined );
		
		quarantined.clear();
		
		for( Agent a : this.getVaccinatedProtecteds() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getVaccinatedProtecteds().removeAll( quarantined ); 
		this.getQuarantinedVaccinatedProtecteds().addAll( quarantined );
		
		quarantined.clear();
		for( Agent a : this.getRemoveds() ) {
			if( !a.isQuarantined() && ( Model.unifFromZeroToOne.sample() < rate ) ){
				a.beQuarantined( pars );
				quarantined.add( a );
			}
		}
		this.getRemoveds().removeAll( quarantined ); 
		this.getIsolatedRemoveds().addAll( quarantined );
	}


	
	//////////////////////////////////////////////////////////
	// vaccination(  double vaccProbSusc, double vaccProbExp )
	//
	public void vaccination( Parameters pars, double vaccProbPerStepSusc, double vaccProbPerStepExp, double vaccReceivingProbPerStep ){
		ArrayList<Agent> agentVaccinated = new ArrayList<Agent>();
		ArrayList<Agent> susc = this.getSusceptibles();
		ArrayList<Agent> exposed = this.getExposeds();
		ArrayList<Agent> quarantinedSusc = this.getQuarantinedSusceptibles();
		ArrayList<Agent> quarantinedExp = this.getQuarantinedExposeds();
		// Ensure that people get vaccinated only once for the correct number of vaccines, i.e., account for those who receive vaccine and don't become immune
		ArrayList<Agent> vaccReceivedAlready = this.getVaccineReceived() ;
		susc.removeAll( vaccReceivedAlready ); 
		exposed.removeAll( vaccReceivedAlready );
		quarantinedSusc.removeAll( vaccReceivedAlready );
		quarantinedExp.removeAll( vaccReceivedAlready );
		for( Agent a : susc ) {
			double u = Model.unifFromZeroToOneVacc.sample(); 
			if( u < vaccProbPerStepSusc ) {
				a.setDaySinceVaccination( 0.0 );
				a.gammaDelayVaccineInducedImmunity();
				a.setInfectionStatus( "VS" ); // efficacy is interpreted as being the fraction to be 
				agentVaccinated.add( a );
				
			}
			if( u < vaccReceivingProbPerStep ) {
				vaccReceivedAlready.add( a );
				pars.setCumulVaccDose( pars.getCumulVaccDose() + 1 );
			}
		}
		this.getVaccinatedSusceptibles().addAll( agentVaccinated );
		susc.removeAll( agentVaccinated );
		agentVaccinated.clear();
		
		for( Agent a : exposed ) {
			double u = Model.unifFromZeroToOneVacc.sample();
			if( u < vaccProbPerStepExp ) {
				a.setDaySinceVaccination( 0.0 );
				a.gammaDelayVaccineInducedImmunity();
				a.setInfectionStatus( "VE" );
				agentVaccinated.add( a );
			}
			if( u < vaccReceivingProbPerStep ) {
				vaccReceivedAlready.add( a );
				pars.setCumulVaccDose( pars.getCumulVaccDose() + 1 );
			}
		}
		this.getVaccinatedExposeds().addAll( agentVaccinated );
		exposed.removeAll( agentVaccinated );
		agentVaccinated.clear();
		
		for( Agent a : quarantinedSusc ) {
			double u = Model.unifFromZeroToOneVacc.sample(); 
			if( u < vaccProbPerStepSusc ) {
				a.setDaySinceVaccination( 0.0 );
				a.gammaDelayVaccineInducedImmunity();
				a.setInfectionStatus( "QVS" ); // efficacy is interpreted as being the fraction to be 
				agentVaccinated.add( a );
				
			}
			if( u < vaccReceivingProbPerStep ) {
				vaccReceivedAlready.add( a );
				pars.setCumulVaccDose( pars.getCumulVaccDose() + 1 );
			}
		}
		this.getQuarantinedVaccinatedSusceptibles().addAll( agentVaccinated );
		quarantinedSusc.removeAll( agentVaccinated );
		agentVaccinated.clear();
		
		for( Agent a : quarantinedExp ) {
			double u = Model.unifFromZeroToOneVacc.sample();
			if( u < vaccProbPerStepExp ) {
				a.setDaySinceVaccination( 0.0 );
				a.gammaDelayVaccineInducedImmunity();
				a.setInfectionStatus( "QVE" );
				agentVaccinated.add( a );
			}
			if( u < vaccReceivingProbPerStep ) {
				vaccReceivedAlready.add( a );
				pars.setCumulVaccDose( pars.getCumulVaccDose() + 1 );
			}
		}
		this.getQuarantinedVaccinatedExposeds().addAll( agentVaccinated );
		quarantinedExp.removeAll( agentVaccinated );
		agentVaccinated.clear();
	}
	
	
	


	////////////////////////////////////////////////////////////////////////////////
	// selectHospital()
	// select one hospital to move arch hospitals within certain distance
	//
	public Hospital selectHospital( Parameters pars ){
		ArrayList<Hospital> targetHosp = new ArrayList<Hospital>();
		double distCutoff = pars.getRadiusHospitalSearch();
		double myPop = this.getPopulationSize();
		double myLon = this.getLongitude();
		double myLat = this.getLatitude();
		int iter = Model.uninfectedHospitals.size(); 
		// case can go to any hospital from the Level 4 hospitals in Seoul
		ArrayList<Hospital> hospitalPool = new ArrayList<Hospital>();
		hospitalPool.addAll( Model.hospitals );
		hospitalPool.addAll( Model.uninfectedHospitals );
		hospitalPool.removeAll( Model.hospitalsCaseIsolated );
		if( this.getLevel() == 4 && this.getRegionID() == 8 ) { 
			targetHosp.addAll( hospitalPool );
		}
		else {
			for( Hospital h : hospitalPool ) {
				double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
				if( d < distCutoff || ( h.getLevel() == 4 && h.getRegionID() == 8 ) ) {// Level 4 Hospitals in Seoul are always accessible 
					targetHosp.add( h );
				}
			}
		}
		
		if( targetHosp.size() > 0 ) {// no target hospital then stay where you are			 
			int N = targetHosp.size();
			int[] popCumSum = new int[ N ];
			int popsize = targetHosp.get(0).getPopulationSize();
			popCumSum[ 0 ] = popsize * popsize;
	        for( int i = 1; i < N; i++ ) {
	        	int popsize1 = targetHosp.get(i).getPopulationSize();
	        	popCumSum[ i ] = popCumSum[ i-1  ] +  popsize1 * popsize1;
	        }
	        int index = 0;
	        double r = Model.unifFromZeroToOne.sample();
	        for( int i = 0; i < N; ++ i ) {       	
	        	if( r <= ( (double) popCumSum[ i ] / popCumSum[ N-1 ] ) ) {
	        		index = i;
	        		break;
	        	}
	        }
		    return targetHosp.get( index );
		} else {
			return null;
		}
	}
	
	
	
/*	
	////////////////////////////////////////////////////////////////////////////////
	// selectHospital()
	// select one hospital to move arch hospitals within certain distance
	//
	public Hospital selectHospital( Parameters pars ){
		ArrayList<Hospital> targetHospList = new ArrayList<Hospital>();
		double distCutoff = pars.getRadiusHospitalSearch();
		double myPop = this.getPopSize();
		double myLon = this.getLongitude();
		double myLat = this.getLatitude();
		int iter = Model.uninfectedHospitals.size(); 
		for( int i = 0; i < iter; ++i ) {
			Hospital h = Model.uninfectedHospitals.get( i );
			double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
//			if( d < distCutoff && myPop < h.getPopSize() ) {
			if( d < distCutoff || ( h.getLevel() == 4 && h.getRegionID() == 8 ) ) {// Level 4 Hospitals in Seoul are always accessible 
				targetHospList.add( h );
			}
		}
				
//		for( int k = 0; k < targetHospList.size(); ++k ) {
//			System.out.println( "time = " + Step.currentDay + ", "
//		
//					+ "level = " +  targetHospList.get(k).getLevel() 
//					+ ", regionID = " +  targetHospList.get(k).getRegionID() 
//					+ ", pop at risk = " +  targetHospList.get(k).getPopSize() );
//		}
		// select hospital in favor of those with larger population (in fact, square of population size)
		if( targetHospList.size() > 0 ) {// no target hospital then stay where you are			 
			int N = targetHospList.size();
			int[] popCumSum = new int[ N ];
			int popsize = targetHospList.get(0).getPopSize();
			popCumSum[ 0 ] = popsize * popsize;
	        for( int i = 1; i < N; i++ ) {
	        	int popsize1 = targetHospList.get(i).getPopSize();
	        	popCumSum[ i ] = popCumSum[ i-1  ] +  popsize1 * popsize1;
	        }
	            
	        int index = 0;
	        double r = Model.unifFromZeroToOne.sample();
	        for( int i = 0; i < N; i++ ) {       	
	        	if( r <= ( (double) popCumSum[i] / popCumSum[N-1] ) ) {
	        		index = i;
	        		break;
	        	}
	        }
//			
//	        System.out.println( "time = " + Step.currentDay + ", selected id = " +  targetHospList.get(index).getID() );
			
		    return targetHospList.get(index);
		} else {
			return null;
		}
	}
	
*/	
	
	

	////////////////////////////////////////////////////////////////////////////////
	// selectHospitalsForVaccinationByDistance()
	// select hospitals within a prespecified distance from the hospitals where cases are detected
	//
	public ArrayList<Hospital> selectHospitalsForVaccinationByDistance( Parameters pars ){
		Set<Hospital> vaccTargetHospList = new HashSet<Hospital>();
		double distCutoff = pars.getVaccinationTargetRadius();
		ArrayList<Hospital> allHospitals = new ArrayList<Hospital>();
		allHospitals.addAll( Model.hospitals );
		allHospitals.addAll( Model.uninfectedHospitals );
		int iter = allHospitals.size(); 
		for( Hospital hosp : Model.hospitals ) {
			// vaccination happens in the hospitals that are within a certain distance from the hospital where cases were isolated			
			if( hosp.getIsolateds().size() > 0 ) { 
				for( int i = 0; i < iter; ++i ) {
					double myLon = hosp.getLongitude();
					double myLat = hosp.getLatitude();
					Hospital h = allHospitals.get(i);
					double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
					if( d < distCutoff ) {
						vaccTargetHospList.add( h );
					}
				}
			}
		}
		return (ArrayList<Hospital>) vaccTargetHospList;
	}

/*		

	////////////////////////////////////////////////////////////////////////////////
	// selectHospitalsForVaccinationByDistance()
	// select hospitals within a prespecified distance from the hospitals where cases are detected
	//
	public ArrayList<Hospital> selectHospitalsForVaccinationByDistance( Parameters pars ){
		ArrayList<Hospital> targetHospList = new ArrayList<Hospital>();
		double distCutoff = pars.getVaccinationTargetRadius();
		double myLon = this.getLongitude();
		double myLat = this.getLatitude();
		ArrayList<Hospital> allHospitals = new ArrayList<Hospital>();
		allHospitals.addAll( Model.hospitals );
		allHospitals.addAll( Model.uninfectedHospitals );
		int iter = allHospitals.size(); 
		for( int i = 0; i < iter; ++i ) {
			Hospital h = allHospitals.get(i);
			double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
			if( d < distCutoff ) {
				targetHospList.add( h );
			}
		}
		return targetHospList;
		
	}

		
*/
	////////////////////////////////////////////////////////////////////////////////
	// electHospitalsForVaccination()
	// select hospitals within a fixed distance from the hospitals where cases are detected
	//
	public ArrayList<Hospital> selectHospitalsForVaccination( Parameters pars ){
		ArrayList<Hospital> targetHospList = new ArrayList<Hospital>();
		double distCutoff = pars.getRadiusHospitalSearch();
		double myLon = this.getLongitude();
		double myLat = this.getLatitude();
		int iter = Model.uninfectedHospitals.size(); 
		for( int i = 0; i < iter; ++i ) {
			Hospital h = Model.uninfectedHospitals.get(i);
			double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
			if( d < distCutoff ) {
				targetHospList.add( h );
			}
		}
		Collections.sort( targetHospList, new SortByPop() );
		if( targetHospList.size() > 0 && targetHospList.size() <= 5 ) {
			return targetHospList;
		}
		else if( targetHospList.size() > 5 ) {
			ArrayList<Hospital> list = new ArrayList<Hospital>();
			for( int r = 0; r < 5; ++r ) {
				list.add( targetHospList.get(r) );
			}
			return list;
		} else {
			return null;
		}
	}
	
	
	////////////////////////////////////////////////////////////////////
	class SortByPop implements Comparator<Hospital> { 
	    public int compare(Hospital a, Hospital b) { 
	        return b.getPopulationSize() - a.getPopulationSize(); 
	    } 
	}
	
	//////////////////////////////////////////////////////////////////////////////
	//
	public ArrayList<Hospital> getTargetHospitalList(Parameters pars){
		ArrayList<Hospital> targetHospList = new ArrayList<Hospital>();
		double distCutoff = pars.getRadiusHospitalSearch();
		double myPop = this.getPopulationSize();
		double myLon = this.getLongitude();
		double myLat = this.getLatitude();
		int iter = Model.uninfectedHospitals.size(); 
		for( int i = 0; i < iter; ++i ) {
			Hospital h = Model.uninfectedHospitals.get(i);
			double d = util.getDistance( myLat, h.getLatitude(), myLon, h.getLongitude() ); 
			if( d < distCutoff  && myPop < h.getPopulationSize() ) {
				targetHospList.add( h );
			}
		}
		return targetHospList;
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////
	// getFracSusceptibles()
	// return the fraction of susceptibles, which is used to proportionally reduce the number of secondary cases
	// quarantined susceptibles are excluded
	public double getFracSusceptibles(){
		int numS = this.getSusceptibles().size();
		int numVS = this.getVaccinatedSusceptibles().size();
		int susc = numS + numVS;
		
		double frac = (double) susc / this.getPopulationSize() ;
		return frac;
	}
	

	

	/////////////////////////////////////////////////////////////////////////////////////////////
	// retrieveExposedAgentsFromHospital()
	// agents exposed to either vaccine or the MERS-CoV
	public ArrayList<Agent> retrieveExposedAgentsFromHospital(){
		ArrayList<Agent> list = new ArrayList<Agent>();
		// exposed to the infected persons
		list.addAll( this.getExposeds() );		
		list.addAll( this.getQuarantinedExposeds() );
		list.addAll( this.getInfectious() );
		list.addAll( this.getIsolateds() );
		list.addAll( this.getRemoveds() );
		list.addAll( this.getIsolatedRemoveds() );
		// exposed to the vaccine
		list.addAll( this.getVaccinatedSusceptibles() );
		list.addAll( this.getVaccinatedExposeds() );
		// exposed to the vaccine and then quarantined
		list.addAll( this.getQuarantinedVaccinatedSusceptibles() );
		list.addAll( this.getQuarantinedVaccinatedExposeds() );
		return list;
	}
	

	/////////////////////////////////////////////////////////////////////////////////////////////
	// getPopulationSize()
	// return the fraction of susceptibles, which is used to proportionally reduce the number of secondary cases
	// quarantined susceptibles are excluded
	public int getPopulationSize(){
		int numS = this.getSusceptibles().size();
		int numE = this.getExposeds().size();
		int numI = this.getInfectious().size();
		int numJ = this.getIsolateds().size();
		int numR = this.getRemoveds().size();
		int numJR = this.getIsolatedRemoveds().size();
		int numVS = this.getVaccinatedSusceptibles().size();
		int numVE = this.getVaccinatedExposeds().size();
		int numVP = this.getVaccinatedProtecteds().size(); // 
		int numQS = this.getQuarantinedSusceptibles().size();
		int numQE = this.getQuarantinedExposeds().size(); // 
		int numQVS = this.getQuarantinedVaccinatedSusceptibles().size();
		int numQVE = this.getQuarantinedVaccinatedExposeds().size();
		int numQVP = this.getQuarantinedVaccinatedProtecteds().size(); // 
		
		int pop = numS + numE + numI + numJ + numR + numJR + numVS + numVE + numVP + numQS + numQE + numQVS + numQVE + numQVP;
				
		return pop;
	}
	

	/////////////////////////////////////////////////////////////////////////////////////////////
	// getAgentsFromHospital()
	// 
	public ArrayList<Agent> retrieveAgentsFromHospital(){
		ArrayList<Agent> list = new ArrayList<Agent>();
		list.addAll( this.getSusceptibles() );
		list.addAll( this.getExposeds() );
		list.addAll( this.getInfectious() );
		list.addAll( this.getIsolateds() );
		list.addAll( this.getRemoveds() );
		list.addAll( this.getIsolatedRemoveds() );
		list.addAll( this.getVaccinatedSusceptibles() );
		list.addAll( this.getVaccinatedExposeds() );
		list.addAll( this.getVaccinatedProtecteds() ); // 
		list.addAll( this.getQuarantinedSusceptibles() );
		list.addAll( this.getQuarantinedExposeds() ); // 
		list.addAll( this.getQuarantinedVaccinatedSusceptibles() );
		list.addAll( this.getQuarantinedVaccinatedExposeds() );
		list.addAll( this.getQuarantinedVaccinatedProtecteds() ); // 
				
		return list;
	}
	
	
	/////////////////////////////////////////////////////////////////
	// printSelf()
	// print information such as ID, degree, risk group,  
	public void printSelf () {
		System.out.println( 
				"ID: " + getID() );
	}


	////////////////////////////////////////////////////////////////////////////////////////////
	// setters and getters()
	// note these are class methods, to set class variables
	public void setID ( int i ) {
		ID = i;
    }
	public int getID () {
		return ID;
	}
	public int getRegionID() {
		return regionID;
	}
	public void setRegionID( int id) {
		this.regionID = id;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	public double getLatitude() {
		return latitude;
	}
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
	public int getLevel() {
		return level;
	}
	public void setLevel(int level) {
		this.level = level;
	}
	
	public ArrayList<Agent> getInfectious() {
		return infectious;
	}
	public void setInfectious(ArrayList<Agent> infectious) {
		this.infectious = infectious;
	}
	public ArrayList<Agent> getSusceptibles() {
		return susceptibles;
	}
	public void setSusceptibles(ArrayList<Agent> susceptibles) {
		this.susceptibles = susceptibles;
	}
	public ArrayList<Agent> getExposeds() {
		return exposeds;
	}
	public void setExposeds(ArrayList<Agent> exposeds) {
		this.exposeds = exposeds;
	}
	public ArrayList<Agent> getIsolateds() {
		return isolateds;
	}
	public void setIsolateds(ArrayList<Agent> isolateds) {
		this.isolateds = isolateds;
	}
	public ArrayList<Agent> getRemoveds() {
		return removeds;
	}
	public void setRemoveds(ArrayList<Agent> removeds) {
		this.removeds = removeds;
	}
	public ArrayList<Agent> getVaccinatedSusceptibles() {
		return vaccinatedSusceptibles;
	}
	public void setVaccinatedSusceptibles(ArrayList<Agent> vaccinatedSusceptibles) {
		this.vaccinatedSusceptibles = vaccinatedSusceptibles;
	}
	public ArrayList<Agent> getVaccinatedExposeds() {
		return vaccinatedExposeds;
	}
	public void setVaccinatedExposeds(ArrayList<Agent> vaccinatedExposeds) {
		this.vaccinatedExposeds = vaccinatedExposeds;
	}
	public ArrayList<Agent> getVaccinatedProtecteds() {
		return vaccinatedProtecteds;
	}
	public void setVaccinatedProtecteds(ArrayList<Agent> vaccinatedProtecteds) {
		this.vaccinatedProtecteds = vaccinatedProtecteds;
	}
	public ArrayList<Agent> getQuarantinedSusceptibles() {
		return quarantinedSusceptibles;
	}
	public void setQuarantinedSusceptibles(ArrayList<Agent> quarantinedSusceptibles) {
		this.quarantinedSusceptibles = quarantinedSusceptibles;
	}
	public ArrayList<Agent> getQuarantinedExposeds() {
		return quarantinedExposeds;
	}
	public void setQuarantinedExposeds(ArrayList<Agent> quarantinedExposeds) {
		this.quarantinedExposeds = quarantinedExposeds;
	}
	public ArrayList<Agent> getVaccineReceived() {
		return vaccineReceived;
	}
	public void setVaccineReceived(ArrayList<Agent> vaccineReceived) {
		this.vaccineReceived = vaccineReceived;
	}
	public double getDayVaccinationStarted() {
		return dayVaccinationStarted;
	}
	public void setDayVaccinationStarted(double dayVaccinationStarted) {
		this.dayVaccinationStarted = dayVaccinationStarted;
	}
	public ArrayList<Agent> getIsolatedRemoveds() {
		return isolatedRemoveds;
	}
	public void setIsolatedRemoveds(ArrayList<Agent> isolatedRemoveds) {
		this.isolatedRemoveds = isolatedRemoveds;
	}
	public ArrayList<Agent> getQuarantinedVaccinatedSusceptibles() {
		return quarantinedVaccinatedSusceptibles;
	}
	public void setQuarantinedVaccinatedSusceptibles(ArrayList<Agent> quarantinedVaccinatedSusceptibles) {
		this.quarantinedVaccinatedSusceptibles = quarantinedVaccinatedSusceptibles;
	}
	public ArrayList<Agent> getQuarantinedVaccinatedExposeds() {
		return quarantinedVaccinatedExposeds;
	}
	public void setQuarantinedVaccinatedExposeds(ArrayList<Agent> quarantinedVaccinatedExposeds) {
		this.quarantinedVaccinatedExposeds = quarantinedVaccinatedExposeds;
	}
	public ArrayList<Agent> getQuarantinedVaccinatedProtecteds() {
		return quarantinedVaccinatedProtecteds;
	}
	public void setQuarantinedVaccinatedProtecteds(ArrayList<Agent> quarantinedVaccinatedProtecteds) {
		this.quarantinedVaccinatedProtecteds = quarantinedVaccinatedProtecteds;
	}
	public boolean isIndexHosp() {
		return indexHosp;
	}
	public void setIndexHosp(boolean indexHosp) {
		this.indexHosp = indexHosp;
	}
}
