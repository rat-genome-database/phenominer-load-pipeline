package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImportTool {

    public static void main(String[] args) throws Exception {

        new ImportTool().run();
    }

    final int sid = 3014; // STUDY_ID
    PhenominerDAO pdao = new PhenominerDAO();

    String rsIdLL = "RS:0000558";
    String rsIdLN = "RS:0000559";
    String rsIdLH = "RS:0000554";

    Map<Integer, String> animalIdMap = new HashMap<>();
    Map<Integer, String> strainMap = new HashMap<>();

    int colMmoId;

    int colRat1;
    int colRatN;

    void run() throws Exception {

        // delete records for all experiments
        if( false ) {
            for (Experiment e : pdao.getExperiments(sid)) {
                List<Record> records = pdao.getRecords(e.getId());
                for (Record r : records) {
                    pdao.deleteRecord(r.getId());
                }
            }
        }

        Study study = pdao.getStudy(sid);
        List<Experiment> experiments = pdao.getExperiments(sid);

        String fname = "/tmp/phenominer submission lh-kwitek-fixed.txt";
        BufferedReader bw = Utils.openReader(fname);

        // CONFIG: column nrs
        int dataLineFirstRow = 6;
        int dataLineLastRow = 55;

        String line0 = bw.readLine();
        String line1 = bw.readLine();
        String line2 = bw.readLine();
        String line3 = bw.readLine();
        loadStrainInfo(line1, line2, line3);

        int lineNr = 4;
        String line;
        while( (line=bw.readLine())!=null ) {
            lineNr++;
            String[] cols = line.split("[\\t]", -1);

            // data line processing
            if( lineNr>=dataLineFirstRow && lineNr<=dataLineLastRow ) {

                String vtID = cols[4];
                String vtName = cols[3];

                // for ClinicalMeasurement
                String cmoId = cols[6];

                // for MeasurementMethod
                String mmoId = cols[colMmoId];

                // for Sample
                int ageInWeeks1 = Integer.parseInt(cols[0]);
                int ageInWeeks2 = Integer.parseInt(cols[1]);

                String units = cols[7];

                Experiment experiment = loadExperiment(vtID, vtName, experiments);
                System.out.println("EID: "+experiment.getId());

                // one line will result in 3 experiment_records: each for a different strain
                Map<String, Record> records = new HashMap<>();
                Map<String, List<IndividualRecord>> irecords = new HashMap<>();

                // gather incoming data
                for( int col=colRat1; col<=colRatN; col++ ) {
                    // is there a value available?
                    String val = cols[col];
                    if( Utils.isStringEmpty(val) ) {
                        continue;
                    }
                    // determine a strain
                    String strainName = strainMap.get(col);
                    Record er = records.get(strainName);
                    List<IndividualRecord> irecList;
                    if( er==null ) {
                        // initialize record for this strain
                        er = new Record();
                        Sample s = new Sample();
                        switch(strainName) {
                            case "LL":
                                s.setStrainAccId(rsIdLL);
                                break;
                            case "LN":
                                s.setStrainAccId(rsIdLN);
                                break;
                            case "LH":
                                s.setStrainAccId(rsIdLH);
                                break;
                        }
                        s.setAgeDaysFromLowBound(ageInWeeks1*7);
                        s.setAgeDaysFromHighBound(ageInWeeks2*7);
                        s.setSex("male");
                        er.setSample(s);

                        ClinicalMeasurement cm = new ClinicalMeasurement();
                        cm.setAccId(cmoId);
                        er.setClinicalMeasurement(cm);

                        MeasurementMethod mm = new MeasurementMethod();
                        mm.setAccId(mmoId);
                        er.setMeasurementMethod(mm);

                        List<Condition> conditionList = new ArrayList<Condition>();
                        for( int ecCol=colMmoId+1; ; ecCol+=4 ) {
                            String xcoId = cols[ecCol+1];
                            if( Utils.isStringEmpty(xcoId) ) {
                                break;
                            }
                            Condition cond = new Condition();
                            cond.setOntologyId(xcoId);
                            long duration = Long.parseLong(cols[ecCol+3]);
                            cond.setDurationLowerBound(duration);
                            cond.setDurationUpperBound(duration);
                            // we handle only % values
                            String value = cols[ecCol+2];
                            if( !Utils.isStringEmpty(value) ) {
                                if( value.endsWith("%") ) {
                                    cond.setValue(value.substring(0, value.length()-1));
                                    cond.setUnits("%");
                                }
                            }
                            conditionList.add(cond);
                            cond.setOrdinality(conditionList.size());
                        }
                        er.setConditions(conditionList);
                        er.setExperimentId(experiment.getId());
                        records.put(strainName, er);

                        irecList = new ArrayList<>();
                        irecords.put(strainName, irecList);
                    } else {
                        irecList = irecords.get(strainName);
                    }
                    IndividualRecord ir = new IndividualRecord();
                    ir.setAnimalId(animalIdMap.get(col));
                    ir.setMeasurementValue(val);
                    irecList.add(ir);
                }

                // compute aggregate variables and save into db
                for( Map.Entry<String,Record> entry: records.entrySet() ) {
                    Record rec = entry.getValue();
                    String strainName = entry.getKey();
                    List<IndividualRecord> irecList = irecords.get(strainName);
                    int N = irecList.size();
                    rec.getSample().setNumberOfAnimals(N);
                    rec.setHasIndividualRecord(true);
                    rec.setMeasurementUnits(units);
                    // compute avg value
                    double sum = 0.0;
                    for( IndividualRecord ir: irecList ) {
                        sum += Double.parseDouble(ir.getMeasurementValue());
                    }
                    Double mean = sum / N;
                    rec.setMeasurementValue(mean.toString());

                    if( irecList.size()>1 ) {
                        // compute variance
                        sum = 0.0;
                        for (IndividualRecord ir : irecList) {
                            double diff = Double.parseDouble(ir.getMeasurementValue()) - mean;
                            sum += diff * diff;
                        }
                        Double variance = sum / (N - 1);
                        Double SD = Math.sqrt(variance);
                        rec.setMeasurementSD(SD.toString());
                        Double SEM = SD / Math.sqrt(N);
                        rec.setMeasurementSem(SEM.toString());
                    }

                    pdao.insertRecord(rec);
                    for (IndividualRecord ir : irecList) {
                        ir.setRecordId(rec.getId());
                        pdao.insertIndividualRecord(ir);
                    }
                }
            }
        }

        bw.close();
    }

    void loadStrainInfo(String line1, String line2, String line3) {
        String[] cols1 = line1.split("[\\t]", -1);
        String[] cols2 = line2.split("[\\t]", -1);
        String[] cols3 = line3.split("[\\t]", -1);

        for( int i=0; i<cols1.length; i++ ) {
            if( cols1[i].startsWith("Rat") ) {
                animalIdMap.put(i, cols2[i]);
                strainMap.put(i, cols3[i]);
                if( colRat1==0 ) {
                    colRat1 = i;
                }
                colRatN = i;
            }
            if( cols1[i].startsWith("Measurement Method Ontology ID") ) {
                colMmoId = i;
            }
        }
    }

    Experiment loadExperiment(String vtID, String vtName, List<Experiment> experimentsInRgd) throws Exception {

        // find a matching experiment in RGD
        for( Experiment exp: experimentsInRgd ) {
            if( exp.getTraitOntId().equals(vtID) ) {
                return exp;
            }
        }

        // not in RGD yet: add to RGD DB and then to the list
        Experiment exp = new Experiment();
        exp.setName(vtName);
        exp.setStudyId(sid);
        exp.setTraitOntId(vtID);
        pdao.insertExperiment(exp);
        experimentsInRgd.add(exp);
        return exp;
    }
}


