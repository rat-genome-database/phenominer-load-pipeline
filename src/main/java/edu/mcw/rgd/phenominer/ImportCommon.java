package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.datamodel.pheno.Record;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.util.ArrayList;
import java.util.List;

/** common code to be used by specialized loaders */

public class ImportCommon {

    PhenominerDAO pdao = new PhenominerDAO();

    Experiment loadExperiment(int sid, String vtID, String vtName, List<Experiment> experimentsInRgd) throws Exception {

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

    edu.mcw.rgd.datamodel.pheno.Record parseStrainRecord(int expId, String cmoId, String[] cols, int strainCol, String rsId, String units) {

        boolean noStrainInfo = Utils.isStringEmpty( String.join("", cols[strainCol], cols[strainCol+1], cols[strainCol+2], cols[strainCol+3], cols[strainCol+4], cols[strainCol+5], cols[strainCol+6]));
        if( noStrainInfo ) {
            return null;
        }

        Record er = new Record();
        er.setExperimentId(expId);

        er.setMeasurementUnits(units);
        er.setMeasurementValue(cols[strainCol]);
        er.setMeasurementSD(cols[strainCol+1]);
        er.setMeasurementSem(cols[strainCol+2]);

        Sample s = new Sample();
        s.setStrainAccId(rsId);

        // handle age range
        String age = cols[strainCol+4];
        int dashPos = age.indexOf('-');
        if( dashPos>0 ) {
            s.setAgeDaysFromLowBound(Integer.parseInt(age.substring(0, dashPos)));
            s.setAgeDaysFromHighBound(Integer.parseInt(age.substring(dashPos+1)));
        } else {
            s.setAgeDaysFromLowBound(Integer.parseInt(age));
            s.setAgeDaysFromHighBound(s.getAgeDaysFromLowBound());
        }

        s.setNumberOfAnimals(Integer.parseInt(cols[strainCol+3]));

        String sex = cols[strainCol+6].toLowerCase().trim();
        s.setSex(sex);

        er.setSample(s);

        ClinicalMeasurement cm = new ClinicalMeasurement();
        cm.setAccId(cmoId);
        er.setClinicalMeasurement(cm);

        MeasurementMethod mm = new MeasurementMethod();
        mm.setAccId(cols[29]);
        er.setMeasurementMethod(mm);

        return er;
    }

    List<Condition> parseConditions(String[] cols, int colsPerConditionSet) {

        List<Condition> conditionList = new ArrayList<Condition>();
        int xcoCol = 31;
        for( int ecCol=xcoCol; ; ecCol+=colsPerConditionSet ) {
            String xcoId = cols[ecCol+1];
            if( Utils.isStringEmpty(xcoId) ) {
                break;
            }
            Condition cond = new Condition();
            cond.setOntologyId(xcoId);

            // duration string: '2419200 s', or '12 hrs', or '30min'
            String sDuration = cols[ecCol+3].trim();
            if( !Utils.isStringEmpty(sDuration) ) {
                long multiplier = 0;
                if (sDuration.endsWith(" s")) {
                    sDuration = sDuration.substring(0, sDuration.length() - 2).trim();
                    multiplier = 1;
                } else if (sDuration.endsWith(" hrs")) {
                    sDuration = sDuration.substring(0, sDuration.length() - 4).trim();
                    multiplier = 60 * 60; // hours to seconds
                } else if (sDuration.endsWith("min")) {
                    sDuration = sDuration.substring(0, sDuration.length() - 3).trim();
                    multiplier = 60; // minutes to seconds
                }
                long duration = Long.parseLong(sDuration) * multiplier;
                cond.setDurationLowerBound(duration);
                cond.setDurationUpperBound(duration);
            }

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

        return conditionList;
    }

    public void deleteRecord(int recordId) throws Exception {
        try {
            pdao.deleteIndividualRecords(recordId);
        } catch (Exception var9) {
            var9.printStackTrace();
        }

        Record rec = pdao.getRecord(recordId);

        try {
            pdao.deleteExperimentConditions(rec.getConditions());
        } catch (Exception var5) {
            var5.printStackTrace();
        }

        String sql = "delete from experiment_record where experiment_record_id=?";
        pdao.update(sql, new Object[]{recordId});

        try {
            pdao.deleteSample(rec.getSampleId());
        } catch (Exception var8) {
            var8.printStackTrace();
        }

        try {
            pdao.deleteMeasurementMethod(rec.getMeasurementMethodId());
        } catch (Exception var7) {
            var7.printStackTrace();
        }

        try {
            pdao.deleteClinicalMeasurement(rec.getClinicalMeasurementId());
        } catch (Exception var6) {
            var6.printStackTrace();
        }

    }

    Double getDurationInSecs(String duration, String unit) {

        if( duration==null || unit==null ) {
            return null;
        }
        if( duration.equalsIgnoreCase("na") || unit.equalsIgnoreCase("na") ) {
            return null;
        }

        double val = Double.parseDouble(duration);
        if( unit.equals("hr") || unit.equals("hour") ) {
            return val*60*60;
        }
        if( unit.equals("week") ) {
            return val*60*60*24*7;
        }
        return null;
    }
}
