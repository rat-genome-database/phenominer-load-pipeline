package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.datamodel.pheno.Record;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** load phenominer study from Excel templates, Sep, 2023
 *
 */
public class ImportStudy3292 extends ImportCommon {

    public static void main(String[] args) throws Exception {

        try {
            new ImportStudy3292().run();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    final int sid = 3322; // STUDY_ID -- 3292 curation -- 3322 dev

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

        run("../resources/study3292_WKY_male_top.txt");
    }

    void run(String fname) throws Exception {

        System.out.println(fname);

        int recordsInserted = 0;

        BufferedReader bw = Utils.openReader(fname);
        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'
        List<Experiment> experiments = pdao.getExperiments(sid);
        int colPastAnimalData = 0;

        int rat1col = 0;
        int ratncol = 0;
        int xcoCol = 0;

        int row = 0;
        String line;
        while ((line = bw.readLine()) != null) {
            row++;
            String[] cols = line.split("[\\t]", -1);

            // determine columns for individual data and xcoCol
            if (row == 2) {
                for (int x = 1; x<cols.length; x++) {
                    String val = cols[x];
                    if (val.startsWith("rat")) {
                        if( rat1col==0 ) {
                            rat1col = x;
                        }
                    } else {
                        if( rat1col>0 && ratncol==0) {
                            ratncol = x-1;
                            colPastAnimalData = x;
                        }
                    }

                    if( val.startsWith("XCO") && xcoCol==0) {
                        xcoCol = x;
                    }
                }
                continue;
            }

            // load animal ids
            if (row == 3) {
                for (int x = rat1col; x<=ratncol; x++) {
                    String val = cols[x];
                    animalIds.put(x, val);
                }
                continue;
            }

            String rsId = cols[1];
            if( rsId==null || rsId.isEmpty() ) {
                continue;
            }
            String sex = cols[2];
            int ageInDays = Integer.parseInt(cols[3]);

            String vtName = cols[5];
            String vtId = cols[6];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }

            String cmoNotes = cols[7];
            String cmoName = cols[8];
            String cmoId = cols[9];
            String units = parseUnits(cols[10]);
            String cmoSiteName = cols[11];
            String cmoSiteAcc = cols[12]; // UBERON:


            int mmoIdCol = colPastAnimalData;
            for (; mmoIdCol < cols.length; mmoIdCol++) {
                if (cols[mmoIdCol].startsWith("MMO:")) {
                    break;
                }
            }
            String mmoId = cols[mmoIdCol];

            Double mmoDurationInSecs = null;
            String mmoDuration = cols[mmoIdCol+1];
            String mmoDurationUnit = cols[mmoIdCol+2];
            if( !Utils.isStringEmpty(mmoDuration) && !Utils.isStringEmpty(mmoDurationUnit) ) {
                mmoDurationInSecs = getDurationInSecs(mmoDuration, mmoDurationUnit);
            }
            String mmoNotes = null;
            {
                String val = cols[mmoIdCol-2];
                if( !Utils.isStringEmpty(val) ) {
                    mmoNotes = val;
                }
                val = cols[mmoIdCol-3];
                if( !Utils.isStringEmpty(val) ) {
                    if( mmoNotes==null ) {
                        mmoNotes = val;
                    } else {
                        mmoNotes += "; "+val;
                    }
                }
            }


            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: " + experiment.getId());


            // process individual data
            List<IndividualRecord> indData = new ArrayList<>();
            for (int x = rat1col; x < colPastAnimalData; x++) {
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
            s1.setStrainAccId(rsId);
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
            mm.setDuration(mmoDurationInSecs==null ? null : mmoDurationInSecs.toString());
            mm.setNotes(mmoNotes);
            er.setMeasurementMethod(mm);

            List<Condition> conditionList = new ArrayList<Condition>();
            Condition cond = parseCondition(xcoCol, cols);
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
        if( units.equals("grams") ) {
            units = "g";
        }

        return units;
    }

    // condcol: cond name, xco id, cond value, cond duration low, cond duration high, cond duration unit, cond ordinality
    Condition parseCondition(int condCol, String[] cols) throws Exception {

        String condOrdinality = getText(cols[condCol+0]);
        String condName = getText(cols[condCol+1]);
        String xcoId = getText(cols[condCol+2]);
        String condMinValue = getText(cols[condCol+3]);
        String condMaxValue = getText(cols[condCol+4]);
        String condValueUnit = getText(cols[condCol+5]);
        String condMinDuration = getText(cols[condCol+6]);
        String condMinDurationUnit = getText(cols[condCol+7]);
        String condMaxDuration = getText(cols[condCol+8]);
        String condMaxDurationUnit = getText(cols[condCol+9]);
        String applicationMethod = getText(cols[condCol+10]);
        String condNotes = getText(cols[condCol+11]);

        Condition cond = new Condition();
        cond.setOntologyId(xcoId);

        cond.setOrdinality(Integer.parseInt(condOrdinality));

        try {
            double minValue = Double.parseDouble(condMinValue);
            cond.setValueMin(condMinValue);
        } catch( Exception e ) {}

        try {
            double maxValue = Double.parseDouble(condMaxValue);
            cond.setValueMax(condMaxValue);
        } catch( Exception e ) {}

        if( cond.getValueMin()!=null || cond.getValueMax()!=null ) {
            cond.setUnits(condValueUnit);
        }

        Double minDur = getDurationInSecs(condMinDuration, condMinDurationUnit);
        if( minDur!=null ) {
            cond.setDurationLowerBound(minDur);
        }
        Double maxDur = getDurationInSecs(condMaxDuration, condMaxDurationUnit);
        if( maxDur!=null ) {
            cond.setDurationUpperBound(maxDur);
        }

        cond.setApplicationMethod(applicationMethod);
        cond.setNotes(condNotes);

        return cond;
    }

    String getText( String s ) {
        if( s==null ) {
            return null;
        }
        s = s.trim();
        if( s.isEmpty() ) {
            return null;
        }
        String lc = s.toLowerCase();
        if( lc.equals("na") || lc.equals("n/a") ) {
            return null;
        }
        return s;
    }
}