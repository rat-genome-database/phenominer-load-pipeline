package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.datamodel.ontologyx.Ontology;
import edu.mcw.rgd.datamodel.pheno.Record;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

public class ImportStudyFromTemplate {

    public static void main(String[] args) throws Exception {

        new ImportStudyFromTemplate().run();
    }

    final int sid = 3025; // STUDY_ID
    String fname = "/tmp/study3025_Harrill2.csv";
    final String RS_ID1 = "RS:0000681";
    final String RS_ID2 = "RS:0004674";

    PhenominerDAO pdao = new PhenominerDAO();

    void run() throws Exception {

        // delete records for all experiments
        if( true ) {
            for (Experiment e : pdao.getExperiments(sid)) {
                List<Record> records = pdao.getRecords(e.getId());
                for (Record r : records) {
                    pdao.deleteRecord(r.getId());
                }
            }
        }

        Study study = pdao.getStudy(sid);
        List<Experiment> experiments = pdao.getExperiments(sid);

        BufferedReader bw = Utils.openReader(fname);

        String line;
        while( (line=bw.readLine())!=null ) {
            String[] cols = line.split("[\\,]", -1);

            // vt id must be in column 0
            int vt;
            try {
                vt = Integer.parseInt(cols[0]);
            } catch( NumberFormatException e) {
                continue;
            }
            String vtId = Ontology.formatId("VT:"+vt);
            String vtName = cols[2];
            String cmoId = cols[3];
            String units = cols[4];

            Experiment experiment = loadExperiment(vtId, vtName, experiments);
            System.out.println("EID: "+experiment.getId());

            // there are two strains: one record per strain
            int strainCol = 5;
            Record er1 = new Record();
            er1.setExperimentId(experiment.getId());

            er1.setMeasurementUnits(units);
            er1.setMeasurementValue(cols[strainCol]);
            er1.setMeasurementSD(cols[strainCol+1]);
            er1.setMeasurementSem(cols[strainCol+2]);

            Sample s1 = new Sample();
            s1.setStrainAccId(RS_ID1);
            s1.setAgeDaysFromLowBound(Integer.parseInt(cols[strainCol+4]));
            s1.setAgeDaysFromHighBound(s1.getAgeDaysFromLowBound());
            s1.setNumberOfAnimals(Integer.parseInt(cols[strainCol+3]));
            s1.setSex(cols[strainCol+6]);
            er1.setSample(s1);

            ClinicalMeasurement cm = new ClinicalMeasurement();
            cm.setAccId(cmoId);
            er1.setClinicalMeasurement(cm);

            MeasurementMethod mm = new MeasurementMethod();
            mm.setAccId(cols[29]);
            er1.setMeasurementMethod(mm);

            List<Condition> conditionList = new ArrayList<Condition>();
            int xcoCol = 31;
            for( int ecCol=xcoCol; ; ecCol+=4 ) {
                String xcoId = cols[ecCol+1];
                if( Utils.isStringEmpty(xcoId) ) {
                    break;
                }
                Condition cond = new Condition();
                cond.setOntologyId(xcoId);

                // duration string: '2419200 s', or '12 hrs'
                long multiplier = 0;
                String sDuration = cols[ecCol+3];
                if( sDuration.endsWith(" s") ) {
                    sDuration = sDuration.substring(0, sDuration.length() - 2).trim();
                    multiplier = 1;
                } else if( sDuration.endsWith(" hrs") ) {
                    sDuration = sDuration.substring(0, sDuration.length() - 4).trim();
                    multiplier = 60 * 60; // hours to seconds
                }
                long duration = Long.parseLong(sDuration) * multiplier;
                cond.setDurationLowerBound(duration);
                cond.setDurationUpperBound(duration);
                // cond value, f.e. '5 ml/kg'
                String value = cols[ecCol+2];
                int spacePos = value.indexOf(' ');
                if( spacePos>0 ) {
                    cond.setValue(value.substring(0, spacePos));
                    cond.setUnits(value.substring(spacePos+1));
                }
                cond.setApplicationMethod(cols[ecCol+4]);

                conditionList.add(cond);
                cond.setOrdinality(conditionList.size());
            }
            er1.setConditions(conditionList);

            pdao.insertRecord(er1);


            // there are two strains: one record per strain
            Record er2 = new Record();
            er2.setExperimentId(experiment.getId());
            strainCol += 7;

            er2.setMeasurementUnits(units);
            er2.setMeasurementValue(cols[strainCol]);
            er2.setMeasurementSD(cols[strainCol+1]);
            er2.setMeasurementSem(cols[strainCol+2]);

            Sample s2 = new Sample();
            s2.setStrainAccId(RS_ID2);
            s2.setAgeDaysFromLowBound(Integer.parseInt(cols[strainCol+4]));
            s2.setAgeDaysFromHighBound(s1.getAgeDaysFromLowBound());
            s2.setNumberOfAnimals(Integer.parseInt(cols[strainCol+3]));
            s2.setSex(cols[strainCol+6]);
            er2.setSample(s2);

            er2.setClinicalMeasurement(cm);

            er2.setMeasurementMethod(mm);

            er2.setConditions(conditionList);

            pdao.insertRecord(er2);
        }

        bw.close();
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


