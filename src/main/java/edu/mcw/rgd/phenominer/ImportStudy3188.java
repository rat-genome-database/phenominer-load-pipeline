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
    final String SEX = "male";

    void run() throws Exception {

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

        Study study = pdao.getStudy(sid);
        List<Experiment> experiments = pdao.getExperiments(sid);
        int recordsInserted = 0;

        Map<Integer, String> animalIds = new HashMap<>(); // excel col --> 'animal_id'

        String fname = "../resources/study3188_individual.txt";
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
                for( int x=7; x<7+NUMBER_OF_ANIMALS; x++ ) {
                    String val = cols[x];
                    if( !Utils.isStringEmpty(val) ) {
                        animalIds.put(x, val);
                    }
                }
                continue;
            }

            // vt id must be in column 3
            String vtId = cols[3];
            if (!vtId.startsWith("VT:") || vtId.length() != 10) {
                continue;
            }
            int ageInWeeks = Integer.parseInt(cols[0]);
            String vtName = cols[2];
            String cmoNotes = cols[1];
            String cmoId = cols[5];
            String cmoName = cols[4];
            String units = cols[6];
            String mmoId = cols[480];
            String xcoId = cols[482];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: " + experiment.getId());

            // process individual data
            List<IndividualRecord> indData = new ArrayList<>();
            for( int x=7; x<7+NUMBER_OF_ANIMALS; x++ ) {
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
            s1.setSex(SEX);
            er1.setSample(s1);

            ClinicalMeasurement cm = new ClinicalMeasurement();
            cm.setAccId(cmoId);
            cm.setNotes(cmoNotes);
            er1.setClinicalMeasurement(cm);

            MeasurementMethod mm = new MeasurementMethod();
            mm.setAccId(mmoId);
            er1.setMeasurementMethod(mm);

            List<Condition> conditionList = new ArrayList<Condition>();
            Condition cond = new Condition();
            cond.setOntologyId(xcoId);
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
    }

}
