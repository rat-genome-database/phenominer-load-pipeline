package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** load phenominer study from Excel template, Apr 4, 2022
 *
 */
public class ImportStudy3188 extends ImportCommon {

    public static void main(String[] args) throws Exception {

        new ImportStudy3188().run();
    }

    final int sid = 3188; // STUDY_ID
    final String STRAIN_ONT_ID="RS:0002539";
    final int NUMBER_OF_ANIMALS = 467;

    void run() throws Exception {

        System.out.println(pdao.getConnectionInfo());

        // delete records for all experiments
        if( true ) {
            int recordsDeleted = 0;
            for (Experiment e : pdao.getExperiments(sid)) {
                List<Record> records = pdao.getRecords(e.getId());
                for (Record r : records) {
                    deleteRecord(r.getId());
                    recordsDeleted++;
                }
            }
            System.out.println("records deleted: "+recordsDeleted);
        }

        if( loadAsIndividualData() ) {
            return;
        }

        ///////
        /// load individual data NOT as individual data
        //

        Study study = pdao.getStudy(sid);
        List<Experiment> experiments = pdao.getExperiments(sid);
        int recordsInserted = 0;

        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'

        String fname = "../resources/study3188_ver2.txt";
        BufferedReader bw = Utils.openReader(fname);

        int row = 0;
        String line;
        while( (line=bw.readLine())!=null ) {
            row++;
            String[] cols = line.split("[\\t]", -1);
            if (cols.length < 483) {
                continue;
            }

            // load animal ids
            if( row==3 ) {
                for( int x=9; x<9+NUMBER_OF_ANIMALS; x++ ) {
                    String val = cols[x];
                    if( !Utils.isStringEmpty(val) ) {
                        animalIds.put(x, val);
                    }
                }
                continue;
            }

            // vt id must be in column 5
            String vtId = cols[5];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }
            int ageInWeeks = Integer.parseInt(cols[0]);
            String animalCount = cols[1];
            String sex = cols[2];
            String cmoNotes = cols[3];
            String vtName = cols[4];
            String cmoName = cols[6];
            String cmoId = cols[7];
            String units = cols[8];
            String mmoId = cols[482];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: " + experiment.getId());

            // process individual data as experiment records
            int nrOfAnimals = Integer.parseInt(animalCount);
            for( int x=9; x<9+NUMBER_OF_ANIMALS; x++ ) {
                String val = cols[x];
                if( !Utils.isStringEmpty(val) ) {

                    Record er = new Record();
                    er.setExperimentId(experiment.getId());
                    er.setExperimentName(vtName);
                    er.setMeasurementUnits(units);

                    Sample s1 = new Sample();
                    int ageInDays = 7*ageInWeeks;
                    s1.setStrainAccId(STRAIN_ONT_ID);
                    s1.setAgeDaysFromLowBound(ageInDays);
                    s1.setAgeDaysFromHighBound(ageInDays);
                    s1.setNumberOfAnimals(nrOfAnimals);
                    s1.setSex(sex);
                    s1.setBioSampleId(animalIds.get(x));
                    er.setSample(s1);

                    ClinicalMeasurement cm = new ClinicalMeasurement();
                    cm.setAccId(cmoId);
                    cm.setNotes(cmoNotes);
                    er.setClinicalMeasurement(cm);

                    MeasurementMethod mm = new MeasurementMethod();
                    mm.setAccId(mmoId);
                    er.setMeasurementMethod(mm);

                    List<Condition> conditionList = new ArrayList<Condition>();
                    Condition cond = parseCondition(483, cols);
                    conditionList.add(cond);
                    er.setConditions(conditionList);

                    er.setMeasurementValue(val);
                    er.setHasIndividualRecord(false);

                    pdao.insertRecord(er);

                    recordsInserted++;
                }
            }
        }

        bw.close();

        System.out.println("OK -- records inserted "+recordsInserted);
    }

    // condcol: cond name, xco id, cond value, cond duration low, cond duration high, cond duration unit, cond ordinality
    Condition parseCondition(int condCol, String[] cols) throws Exception {

        String condName = cols[condCol+0];
        String xcoId = cols[condCol+1];
        String condValue = cols[condCol+2];
        String condDurLow = cols[condCol+3];
        String condDurHigh = cols[condCol+4];
        String condDurUnit = cols[condCol+5].toLowerCase();
        String condOrdinality = cols[condCol+6];

        Condition cond = new Condition();
        cond.setOntologyId(xcoId);

        // duration is stored in db in seconds
        long multiplier = 0;
        if( !condDurUnit.isEmpty() ) {
            if( condDurUnit.equals("hours") ) {
                multiplier = 60 * 60; // hours to seconds
            } else {
                throw new Exception("unexpected duration unit");
            }
        }

        // duration string: f.e. '16'
        if( !Utils.isStringEmpty(condDurLow) ) {
            long duration = Long.parseLong(condDurLow) * multiplier;
            cond.setDurationLowerBound(duration);
        }
        if( !Utils.isStringEmpty(condDurHigh) ) {
            long duration = Long.parseLong(condDurHigh) * multiplier;
            cond.setDurationUpperBound(duration);
        }

        // cond value
        if( !(condValue.isEmpty() || condValue.toUpperCase().equals("N/A")) ) {
            throw new Exception("unexpected: add code to handle condition values");
        }
        cond.setOrdinality(Integer.parseInt(condOrdinality));

        return cond;
    }

    boolean loadAsIndividualData() throws Exception {

        Study study = pdao.getStudy(sid);
        List<Experiment> experiments = pdao.getExperiments(sid);
        int recordsInserted = 0;

        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'

        //String fname = "../resources/study3188_individual.txt";
        String fname = "../resources/study3188_ver2.txt";
        BufferedReader bw = Utils.openReader(fname);

        int row = 0;
        String line;
        while( (line=bw.readLine())!=null ) {
            row++;
            String[] cols = line.split("[\\t]", -1);
            if (cols.length < 483) {
                continue;
            }

            // load animal ids
            if( row==3 ) {
                for( int x=9; x<9+NUMBER_OF_ANIMALS; x++ ) {
                    String val = cols[x];
                    if( !Utils.isStringEmpty(val) ) {
                        animalIds.put(x, val);
                    }
                }
                continue;
            }

            // vt id must be in column 5
            String vtId = cols[5];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }
            int ageInWeeks = Integer.parseInt(cols[0]);
            String animalCount = cols[1];
            String sex = cols[2];
            String cmoNotes = cols[3];
            String vtName = cols[4];
            String cmoName = cols[6];
            String cmoId = cols[7];
            String units = cols[8];
            String mmoId = cols[482];
            String xcoId = cols[484];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: " + experiment.getId());

            // process individual data
            List<IndividualRecord> indData = new ArrayList<>();
            for( int x=9; x<9+NUMBER_OF_ANIMALS; x++ ) {
                String val = cols[x];
                if( !Utils.isStringEmpty(val) ) {
                    IndividualRecord indRec = new IndividualRecord();
                    indRec.setAnimalId(animalIds.get(x));
                    indRec.setMeasurementValue(val);
                    indData.add(indRec);
                }
            }
            int nrOfAnimals = indData.size();

            Record er1 = new Record();
            er1.setExperimentId(experiment.getId());
            er1.setExperimentName(vtName);
            er1.setMeasurementUnits(units);

            Sample s1 = new Sample();
            int ageInDays = 7*ageInWeeks;
            s1.setStrainAccId(STRAIN_ONT_ID);
            s1.setAgeDaysFromLowBound(ageInDays);
            s1.setAgeDaysFromHighBound(ageInDays);
            s1.setNumberOfAnimals(nrOfAnimals);
            s1.setSex(sex);
            er1.setSample(s1);

            ClinicalMeasurement cm = new ClinicalMeasurement();
            cm.setAccId(cmoId);
            cm.setNotes(cmoNotes);
            er1.setClinicalMeasurement(cm);

            MeasurementMethod mm = new MeasurementMethod();
            mm.setAccId(mmoId);
            er1.setMeasurementMethod(mm);

            List<Condition> conditionList = new ArrayList<Condition>();
            Condition cond = parseCondition(483, cols);
            conditionList.add(cond);
            cond.setOrdinality(conditionList.size());
            er1.setConditions(conditionList);

            double avg = 0.0, sd = 0.0, sem = 0.0;
            if( nrOfAnimals==1 ) {
                avg = Double.parseDouble(indData.get(0).getMeasurementValue());
            } else if( nrOfAnimals>1 ) {

                for( int i=0; i<indData.size(); i++ ) {
                    avg += Double.parseDouble(indData.get(i).getMeasurementValue());
                }
                avg /= nrOfAnimals;

                double sum2 = 0.0;
                for( int i=0; i<indData.size(); i++ ) {
                    double dval = Double.parseDouble(indData.get(i).getMeasurementValue());
                    sum2 += (dval-avg)*(dval-avg);
                }
                sd = Math.sqrt(sum2 / (nrOfAnimals-1));
                sem = sd / Math.sqrt(nrOfAnimals);
            }

            er1.setMeasurementValue(Float.toString((float)avg));
            er1.setMeasurementSD(Float.toString((float)sd));
            er1.setMeasurementSem(Float.toString((float)sem));
            er1.setHasIndividualRecord(true);

            pdao.insertRecord(er1);

            recordsInserted++;

            for( int i=0; i<indData.size(); i++ ) {
                IndividualRecord irec = indData.get(i);
                irec.setRecordId(er1.getId());
                pdao.insertIndividualRecord(irec);
            }
        }

        bw.close();

        System.out.println("OK -- records inserted "+recordsInserted);
        System.out.println("==== HARD EXIT =====");
        return true;
    }
}
