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
public class ImportStudy3200 extends ImportCommon {

    public static void main(String[] args) throws Exception {

        try {
            new ImportStudy3200().run();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    final int sid = 3200; // STUDY_ID

    void run() throws Exception {

        // delete records for all experiments
        if (true) {
            int recordsDeleted = 0;
            for (Experiment e : pdao.getExperiments(sid)) {
                List<Record> records = pdao.getRecords(e.getId());
                for (Record r : records) {
                    deleteRecord(r.getId());
                    recordsDeleted++;
                }
            }
            System.out.println("records deleted: " + recordsDeleted);
        }

        Study study = pdao.getStudy(sid);

        run("../resources/study3200_ACI.txt", "RS:0003549", "ACI");
        run("../resources/study3200_BN.txt", "RS:0000155", "BN");
        run("../resources/study3200_BUF.txt", "RS:0000167", "BUF");
        run("../resources/study3200_F344.txt", "RS:0000363", "F344");
        run("../resources/study3200_M520.txt", "RS:0000573", "M520");
        run("../resources/study3200_NMcwiHS.txt", "RS:0002539", null);
        run("../resources/study3200_WKY.txt", "RS:0000916", "WKY");
    }

    void run(String fname, String strainOntId, String animalIdPrefix) throws Exception {

        System.out.println(fname + "  " + strainOntId + "    - " + animalIdPrefix);
        if( true ) {
            loadIndividualRecords(fname, strainOntId, animalIdPrefix);
            return;
        }

        int recordsInserted = 0;

        BufferedReader bw = Utils.openReader(fname);
        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'
        List<Experiment> experiments = pdao.getExperiments(sid);
        int colPastAnimalData = 0;

        int row = 0;
        String line;
        while ((line = bw.readLine()) != null) {
            row++;
            String[] cols = line.split("[\\t]", -1);

            // load animal ids
            if (row == 3) {
                for (int x = 9; ; x++) {
                    String val = cols[x];
                    if (val.startsWith(animalIdPrefix)) {
                        animalIds.put(x, val);
                    } else {
                        colPastAnimalData = x;
                        break;
                    }
                }
                continue;
            }

            int ageInWeeks = 0;
            try {
                ageInWeeks = Integer.parseInt(cols[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (ageInWeeks <= 0) {
                continue;
            }
            String animalCount = cols[1];
            String sex = cols[2];
            String cmoNotes = cols[3];
            String vtName = cols[4];
            String vtId = cols[5];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }
            String cmoName = cols[6];
            String cmoId = cols[7];
            String units = parseUnits(cols[8]);

            int mmoIdCol = colPastAnimalData;
            for (; mmoIdCol < cols.length; mmoIdCol++) {
                if (cols[mmoIdCol].startsWith("MMO:")) {
                    break;
                }
            }
            String mmoId = cols[mmoIdCol];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: " + experiment.getId());

            // process individual data as experiment records
            int nrOfAnimals = Integer.parseInt(animalCount);
            for (int x = 9; x < 9 + animalIds.size(); x++) {
                String val = cols[x];
                if (!Utils.isStringEmpty(val)) {

                    Record er = new Record();
                    er.setExperimentId(experiment.getId());
                    er.setExperimentName(vtName);
                    er.setMeasurementUnits(units);

                    Sample s1 = new Sample();
                    int ageInDays = 7 * ageInWeeks;
                    s1.setStrainAccId(strainOntId);
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
                    Condition cond = parseCondition(mmoIdCol + 1, cols);
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

        System.out.println("OK -- records inserted " + recordsInserted);
    }

    String parseUnits(String units) {
        // The 'x'in Phenominer units is always preceded and followed by a space. (STAN)

        // x must be preceded by space
        int xPos = units.indexOf('x');
        if( xPos==0 ) {
            units = " "+units;
        } else if( xPos>0 ) {
            if( units.charAt(xPos-1)!=' ') {
                units = units.substring(0, xPos)+" "+ units.substring(xPos);
            }
        }

        // x must be followed by space
        xPos = units.indexOf('x');
        if( xPos==units.length()-1 ) {
            units += " ";
        } else if( xPos>0 ) {
            if( units.charAt(xPos+1)!=' ') {
                units = units.substring(0, xPos+1)+" "+ units.substring(xPos+1);
            }
        }

        // for this study, convert 'g' into 'grams'
        if( units.equals("g") ) {
            units = "grams";
        }

        return units;
    }

    // condcol: cond name, xco id, cond value, cond duration low, cond duration high, cond duration unit, cond ordinality
    Condition parseCondition(int condCol, String[] cols) throws Exception {

        String condName = cols[condCol+0];
        String xcoId = cols[condCol+1];
        String condValue = cols[condCol+2];
        String condDurLow = cols[condCol+3];
        String condOrdinality = cols[condCol+4];
        //String condDurHigh = cols[condCol+4];
        //String condDurUnit = cols[condCol+5].toLowerCase();
        //String condOrdinality = cols[condCol+6];

        Condition cond = new Condition();
        cond.setOntologyId(xcoId);

        /*
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
        */

        // cond value
        if( !(condValue.isEmpty() || condValue.toUpperCase().equals("N/A")) ) {
            throw new Exception("unexpected: add code to handle condition values");
        }
        cond.setOrdinality(Integer.parseInt(condOrdinality));

        return cond;
    }


    void loadIndividualRecords(String fname, String strainOntId, String animalIdPrefix) throws Exception {

        int recordsInserted = 0;

        BufferedReader bw = Utils.openReader(fname);
        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'
        List<Experiment> experiments = pdao.getExperiments(sid);
        int colPastAnimalData = 0;

        int animalCol1=0, animalCol2=0;
        int row = 0;
        String line;
        while ((line = bw.readLine()) != null) {
            row++;
            String[] cols = line.split("[\\t]", -1);

            // load animal ids
            if( animalIdPrefix==null ) {
                if (row == 3) {
                    // find start and end column with text starting with 'rat'
                    animalCol1 = animalCol2 = 0;
                    for (int x = 9; x<cols.length; x++) {
                        String val = cols[x];
                        if (val.startsWith("rat")) {
                            if( animalCol1==0 ) {
                                animalCol1 = x;
                            }
                            animalCol2 = x;
                        }
                    }
                    colPastAnimalData = animalCol2+1;
                    continue;
                }
                if (row == 4) {
                    for (int x = animalCol1; x<=animalCol2; x++) {
                        String val = cols[x];
                        animalIds.put(x, val);
                    }
                    continue;
                }
            } else {
                // load animal ids
                if (row == 3) {
                    for (int x = 9; ; x++) {
                        String val = cols[x];
                        if (val.startsWith(animalIdPrefix)) {
                            animalIds.put(x, val);
                        } else {
                            colPastAnimalData = x;
                            break;
                        }
                    }
                    animalCol1 = 9;
                    animalCol2 = colPastAnimalData-1;
                    continue;
                }
            }

            int ageInWeeks = 0;
            try {
                ageInWeeks = Integer.parseInt(cols[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (ageInWeeks <= 0) {
                continue;
            }
            String animalCount = cols[1];
            String sex = cols[2];
            String cmoNotes = cols[3];
            String vtName = cols[4];
            String vtId = cols[5];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }
            String cmoName = cols[6];
            String cmoId = cols[7];
            String units = parseUnits(cols[8]);

            int mmoIdCol = colPastAnimalData;
            for (; mmoIdCol < cols.length; mmoIdCol++) {
                if (cols[mmoIdCol].startsWith("MMO:")) {
                    break;
                }
            }
            String mmoId = cols[mmoIdCol];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);

            // process individual data
            List<IndividualRecord> indData = new ArrayList<>();
            for (int x = animalCol1; x < colPastAnimalData; x++) {
                String val = cols[x];
                if( !Utils.isStringEmpty(val) && !val.equals("NA") ) {
                    IndividualRecord indRec = new IndividualRecord();
                    indRec.setAnimalId(animalIds.get(x));
                    indRec.setMeasurementValue(val);
                    indData.add(indRec);
                }
            }
            int nrOfAnimals = indData.size();
            System.out.println("EID: " + experiment.getId()+"   nr of animals: "+nrOfAnimals);

            Record er = new Record();
            er.setExperimentId(experiment.getId());
            er.setExperimentName(vtName);
            er.setMeasurementUnits(units);

            Sample s1 = new Sample();
            int ageInDays = 7 * ageInWeeks;
            s1.setStrainAccId(strainOntId);
            s1.setAgeDaysFromLowBound(ageInDays);
            s1.setAgeDaysFromHighBound(ageInDays);
            s1.setNumberOfAnimals(nrOfAnimals);
            s1.setSex(sex);
            er.setSample(s1);

            ClinicalMeasurement cm = new ClinicalMeasurement();
            cm.setAccId(cmoId);
            cm.setNotes(cmoNotes);
            er.setClinicalMeasurement(cm);

            MeasurementMethod mm = new MeasurementMethod();
            mm.setAccId(mmoId);
            er.setMeasurementMethod(mm);

            List<Condition> conditionList = new ArrayList<Condition>();
            Condition cond = parseCondition(mmoIdCol + 1, cols);
            conditionList.add(cond);
            er.setConditions(conditionList);

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

            er.setMeasurementValue(Float.toString((float)avg));
            er.setMeasurementSD(Float.toString((float)sd));
            er.setMeasurementSem(Float.toString((float)sem));
            er.setHasIndividualRecord(true);

            pdao.insertRecord(er);

            recordsInserted++;

            for( int i=0; i<indData.size(); i++ ) {
                IndividualRecord irec = indData.get(i);
                irec.setRecordId(er.getId());
                pdao.insertIndividualRecord(irec);
            }
        }

        bw.close();

        System.out.println("OK -- records inserted " + recordsInserted);
    }
}
