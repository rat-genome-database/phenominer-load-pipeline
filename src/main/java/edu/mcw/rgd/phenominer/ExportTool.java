package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.dao.DataSourceFactory;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.datamodel.pheno.Record;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class ExportTool {
    static OntologyXDAO odao = new OntologyXDAO();
    static PhenominerDAO pdao = new PhenominerDAO();

    public static void main(String[] args) throws Exception {

        generateFiles();
    }

    static void generateFiles() throws Exception {

        String headerCommonColumns1 =
        "#STUDY NAME\tSTUDY SOURCE\tSTUDY TYPE\tREFERENCE RGD ID\tEXPERIMENT\tEXPERIMENT NOTES\t";

        String headerCommonColumns2 =
        "AVERAGED MEASUREMENT VALUE\tMEASUREMENT UNITS\tMEASUREMENT ERROR\tMEASUREMENT SD\tMEASUREMENT SEM\t"+

        "STRAIN TERM ACC\tSTRAIN NAME\tSEX\t"+
        "NUMBER OF ANIMALS\tAGE IN DAYS LOW BOUND\tAGE IN DAYS HIGH BOUND\tSAMPLE NOTES\t"+
        "MEASUREMENT METHOD ACC\tMEASUREMENT METHOD\tMEASUREMENT SITE\tMEASUREMENT SITE ONT IDS\t"+

        "MEASUREMENT DURATION IN SECS\tMEASUREMENT METHOD NOTES\tMEASUREMENT METHOD PI TYPE\t"+
        "MEASUREMENT METHOD PI TIME VALUE\tMEASUREMENT METHOD PI TIME UNIT\t"+

        "CLINICAL MEASUREMENT ACC\tCLINICAL MEASUREMENT\tCLINICAL MEASUREMENT SITE\tCLINICAL MEASUREMENT SITE ONT ID\t"+
        "CLINICAL MEASUREMENT AVERAGE TYPE\tCLINICAL MEASUREMENT FORMULA\tCLINICAL MEASUREMENT NOTES\t"+

        "COND1 TERM ACC\tCONDITION1\tCOND1 UNITS\t"+
        "COND1 VALUE MIN\tCOND1 VALUE MAX\tCOND1 DURATION MIN\tCOND1 DURATION MAX\t"+
        "COND1 APPLICATION METHOD\tCOND1 NOTES\tCOND1 ORDINALITY\t"+

        "COND2 TERM ACC\tCONDITION2\tCOND2 UNITS\t"+
        "COND2 VALUE MIN\tCOND2 VALUE MAX\tCOND2 DURATION MIN\tCOND2 DURATION MAX\t"+
        "COND2 APPLICATION METHOD\tCOND2 NOTES\tCOND2 ORDINALITY\t"+

        "COND3 TERM ACC\tCONDITION3\tCOND3 UNITS\t"+
        "COND3 VALUE MIN\tCOND3 VALUE MAX\tCOND3 DURATION MIN\tCOND3 DURATION MAX\t"+
        "COND3 APPLICATION METHOD\tCOND3 NOTES\tCOND3 ORDINALITY\t"+

        "COND4 TERM ACC\tCONDITION4\tCOND4 UNITS\t"+
        "COND4 VALUE MIN\tCOND4 VALUE MAX\tCOND4 DURATION MIN\tCOND4 DURATION MAX\t"+
        "COND4 APPLICATION METHOD\tCOND4 NOTES\tCOND4 ORDINALITY\t"+

        "COND5 TERM ACC\tCONDITION1\tCOND5 UNITS\t"+
        "COND5 VALUE MIN\tCOND5 VALUE MAX\tCOND5 DURATION MIN\tCOND5 DURATION MAX\t"+
        "COND5 APPLICATION METHOD\tCOND5 NOTES\tCOND5 ORDINALITY\t"+

        "COND6 TERM ACC\tCONDITION6\tCOND6 UNITS\t"+
        "COND6 VALUE MIN\tCOND6 VALUE MAX\tCOND6 DURATION MIN\tCOND6 DURATION MAX\t"+
        "COND6 APPLICATION METHOD\tCOND6 NOTES\tCOND6 ORDINALITY\n";

        BufferedWriter out = new BufferedWriter(new FileWriter("/tmp/ind.txt"));
        BufferedWriter out2 = new BufferedWriter(new FileWriter("/tmp/pheno.txt"));
        out.write("#All of the phenotype records for mutant and congenic strains for which we have individual animal data for both males and females\n");
        out2.write("#All of the phenotype records for mutant and congenic strains for which we have the sample mean data for both males and females\n");

        String refInfo = "#REFERENCE RGD ID column: to see the reference in RGD, use the url: http://rgd.mcw.edu/rgdweb/report/reference/main.html?id=\n";
        refInfo += "#  f.e.: http://rgd.mcw.edu/rgdweb/report/reference/main.html?id=8142362\n";
        out.write(refInfo);
        out2.write(refInfo);

        out.write(headerCommonColumns1);
        out2.write(headerCommonColumns1);

        out.write("ANIMAL ID\tANIMAL MEASUREMENT VALUE\t");

        out.write(headerCommonColumns2);
        out2.write(headerCommonColumns2);

        String sql0 = "select r1.experiment_record_id er1,r2.experiment_record_id er2\n" +
                "from experiment_record r1,experiment_record r2,sample s1,sample s2,measurement_method m1,measurement_method m2\n" +
                "  ,clinical_measurement c1,clinical_measurement c2,cond_group_experiment_cond g1,cond_group_experiment_cond g2\n" +
                "  ,experiment_condition e1,experiment_condition e2\n" +
                "where r1.experiment_id=r2.experiment_id and r1.curation_status=40 and r2.curation_status=40\n" +
                " and r1.sample_id=s1.sample_id and r2.sample_id=s2.sample_id and s1.strain_ont_id=s2.strain_ont_id\n" +
                "   and s1.sex='male' and s2.sex='female' %CUSTOM% \n" +
                " and r1.measurement_method_id=m1.measurement_method_id and r2.measurement_method_id=m2.measurement_method_id\n" +
                "   and m1.measurement_method_ont_id=m2.measurement_method_ont_id\n" +
                " and r1.clinical_measurement_id=c1.clinical_measurement_id and r2.clinical_measurement_id=c2.clinical_measurement_id\n" +
                "   and c1.clinical_measurement_ont_id=c2.clinical_measurement_ont_id\n" +
                " and r1.condition_group_id=g1.condition_group_id and r2.condition_group_id=g2.condition_group_id\n" +
                "   and g1.experiment_condition_id=e1.experiment_condition_id and g2.experiment_condition_id=e2.experiment_condition_id\n" +
                "   and e1.exp_cond_ont_id=e2.exp_cond_ont_id and e1.exp_cond_ordinality=e2.exp_cond_ordinality\n" +
                " and  s1.strain_ont_id in( \n" +
                "  SELECT CHILD_TERM_ACC FROM ONT_DAG\n" +
                "  START WITH PARENT_TERM_ACC = 'RS:0000459' CONNECT BY PRIOR CHILD_TERM_ACC = PARENT_TERM_ACC\n" +
                "  UNION SELECT CHILD_TERM_ACC FROM ONT_DAG\n" +
                "  START WITH PARENT_TERM_ACC = 'RS:0000461' CONNECT BY PRIOR CHILD_TERM_ACC = PARENT_TERM_ACC\n" +
                "  UNION SELECT 'RS:0000459' FROM DUAL\n" +
                "  UNION SELECT 'RS:0000461' FROM DUAL\n" +
                ")";
        String sqlInd = sql0.replace("%CUSTOM%", "and r1.has_individual_record=1 and r2.has_individual_record=1");

        Connection conn = DataSourceFactory.getInstance().getDataSource().getConnection();
        PreparedStatement ps = conn.prepareStatement(sqlInd);
        ResultSet rs = ps.executeQuery();
        int pairs = 0;
        while( rs.next() ) {
            int er1 = rs.getInt(1);
            int er2 = rs.getInt(2);
            Record r1 = pdao.getRecord(er1);
            Record r2 = pdao.getRecord(er2);

            writeIndRecord(out, r1);
            writeIndRecord(out, r2);
            pairs++;
        }
        ps.close();
        out.close();
        System.out.println("WRITTEN DATA FOR "+pairs+" EXP RECORD PAIRS WITH INDIVIDUAL DATA");

        String sqlRec = sql0.replace("%CUSTOM%", "and r1.measurement_value is not null and r2.measurement_value is not null");
        ps = conn.prepareStatement(sqlRec);
        rs = ps.executeQuery();
        pairs = 0;
        while( rs.next() ) {
            int er1 = rs.getInt(1);
            int er2 = rs.getInt(2);
            Record r1 = pdao.getRecord(er1);
            Record r2 = pdao.getRecord(er2);

            writeRecord(out2, r1);
            writeRecord(out2, r2);
            pairs++;
        }
        ps.close();
        out2.close();
        System.out.println("WRITTEN DATA FOR "+pairs+" EXP RECORD PAIRS");

        conn.close();
    }

    static void writeIndRecord(BufferedWriter out, Record r) throws Exception {

        List<IndividualRecord> inds = pdao.getIndividualRecords(r.getId());
        for( IndividualRecord ir: inds ) {

            String indRecData = ir.getAnimalId()+"\t"+ir.getMeasurementValue()+"\t";
            out.write(generateLine(r, indRecData));
        }
    }

    static void writeRecord(BufferedWriter out, Record r) throws Exception {

        out.write(generateLine(r, null));
    }

    static String generateLine(Record r, String indRecData) throws Exception {
        Study study = pdao.getStudy(r.getStudyId());
        Experiment exp = pdao.getExperiment(r.getExperimentId());
        Sample s = r.getSample();
        Term sterm = odao.getTermWithStatsCached(s.getStrainAccId());
        MeasurementMethod mm = r.getMeasurementMethod();
        Term mterm = odao.getTermWithStatsCached(mm.getAccId());
        ClinicalMeasurement cm = r.getClinicalMeasurement();
        Term cterm = odao.getTermWithStatsCached(cm.getAccId());

        List<Condition> conds = r.getConditions();

        String buf;
        buf  = (study.getName() + "\t" + study.getSource() + "\t" + study.getType() + "\t" + study.getRefRgdId() + "\t");
        buf += (exp.getName() + "\t" + NV(exp.getNotes()) + "\t");

        if( indRecData!=null ) {
            buf += indRecData;
        }

        buf +=(NV(r.getMeasurementValue())+"\t"+NV(r.getMeasurementUnits())+"\t"+NV(r.getMeasurementError())+"\t");
        buf +=(NV(r.getMeasurementSD())+"\t"+NV(r.getMeasurementSem())+"\t");

        buf +=(s.getStrainAccId() + "\t" + sterm.getTerm() + "\t"+s.getSex()+"\t");
        buf +=(NV(s.getNumberOfAnimals()) + "\t" + NV(s.getAgeDaysFromLowBound()) + "\t" + NV(s.getAgeDaysFromHighBound()) + "\t");
        buf +=(NV(s.getNotes()) + "\t");

        buf +=(mterm.getAccId() + "\t" + mterm.getTerm() + "\t" + NV(mm.getSite()) + "\t" + NV(mm.getSiteOntIds()) + "\t");
        buf +=(NV(mm.getDuration()) + "\t" + NV(mm.getNotes()) + "\t" + NV(mm.getPiType()) + "\t");
        buf +=(NV(mm.getPiTimeValue()) + "\t" + NV(mm.getPiTypeUnit()) + "\t");

        buf +=(cterm.getAccId() + "\t" + cterm.getTerm() + "\t" + NV(cm.getSite()) + "\t" + NV(cm.getSiteOntIds()) + "\t");
        buf +=(NV(cm.getAverageType()) + "\t" + NV(cm.getFormula()) + "\t" + NV(cm.getNotes()) + "\t");

        for( int i=0; i<6; i++ ) {
            buf += writeCondition(conds, i);
        }
        buf += "\n";
        return buf;
    }

    static String writeCondition(List<Condition> conds, int i) throws Exception {
        if( i<conds.size() ) {
            Condition cond = conds.get(i);
            Term term = odao.getTermWithStatsCached(cond.getOntologyId());
            return term.getAccId() + "\t" + term.getTerm() + "\t" + NV(cond.getUnits()) + "\t"
                    + NV(cond.getValueMin()) + "\t" + NV(cond.getValueMax()) + "\t" + cond.getDurationLowerBound() + "\t" + cond.getDurationUpperBound() + "\t"
                    + NV(cond.getApplicationMethod()) + "\t" + NV(cond.getNotes()) + "\t" + NV(cond.getOrdinality()) + "\t";
        } else {
            return "\t\t\t\t\t\t\t\t\t\t";
        }
    }

    static String NV(String s) {
        return Utils.defaultString(s);
    }

    static String NV(Integer i) {
        return i==null ? "" : i.toString();
    }
}

