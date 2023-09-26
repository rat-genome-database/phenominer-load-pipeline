package edu.mcw.rgd.phenominer;

import edu.mcw.rgd.datamodel.pheno.Record;
import edu.mcw.rgd.datamodel.pheno.*;
import edu.mcw.rgd.process.Utils;

import java.io.BufferedReader;
import java.util.List;

/** load phenominer study from Excel template, Nov 4, 2020
 *
 */
public class ImportStudy3101 extends ImportCommon {

    public static void main(String[] args) throws Exception {

        new ImportStudy3101().run();
    }

    final int sid = 3101; // STUDY_ID
    final String[] STRAIN_ONT_IDS = { // in order as they appear in the template
            "RS:0000015", // SHR
            "RS:0000742", // WKY
            "RS:0000967", // WAG
    };

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

        String fname = "src/main/resources/study3101_romanowska.txt";
        BufferedReader bw = Utils.openReader(fname);

        String line;
        while( (line=bw.readLine())!=null ) {
            String[] cols = line.split("[\\t]", -1);

            // vt id must be in column 0
            String vtId = cols[0];
            if( !vtId.startsWith("VT:") || vtId.length()!=10 ) {
                continue;
            }
            String vtName = cols[2];
            String cmoId = cols[3];
            String units = cols[4];

            Experiment experiment = loadExperiment(sid, vtId, vtName, experiments);
            System.out.println("EID: "+experiment.getId());

            List<Condition> conditionList = parseConditions(cols, 5);

            // there are three strains, possibly empty; every strain spans 7 columns
            int strainCol = 5;
            Record er1 = parseStrainRecord(experiment.getId(), cmoId, cols, strainCol, STRAIN_ONT_IDS[0], units);

            if( er1!=null ) {
                er1.setConditions(conditionList);
                pdao.insertRecord(er1);
                recordsInserted++;
            }


            // strain 2
            strainCol += 7;
            Record er2 = parseStrainRecord(experiment.getId(), cmoId, cols, strainCol, STRAIN_ONT_IDS[1], units);

            if( er2!=null ) {
                er2.setConditions(conditionList);
                pdao.insertRecord(er2);
                recordsInserted++;
            }


            // strain 3
            strainCol += 7;
            Record er3 = parseStrainRecord(experiment.getId(), cmoId, cols, strainCol, STRAIN_ONT_IDS[2], units);

            if( er3!=null ) {
                er3.setConditions(conditionList);
                pdao.insertRecord(er3);
                recordsInserted++;
            }
        }

        bw.close();

        System.out.println("OK -- records inserted "+recordsInserted);
    }

}
