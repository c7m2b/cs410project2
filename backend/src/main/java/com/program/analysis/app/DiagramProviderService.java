package com.program.analysis.app;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.program.analysis.app.representation.AnalysisConstants;
import com.program.analysis.app.representation.Field;
import com.program.analysis.app.representation.GraphElement;
import com.program.analysis.app.representation.Method;
import com.program.analysis.app.representation.ProjectCollector;
import com.program.analysis.app.representation.ProjectParseException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class DiagramProviderService {
    private ProjectCollector projectCollector = ProjectCollector.getInstance();
    private static ZipFileStorageManager zipFileStorageManager = new ZipFileStorageManager().initDefaultFolders();

    public void upload(MultipartFile multipartFile) throws IOException, ProjectParseException {
        // Store the multipart file to zip folder then unzip it
        zipFileStorageManager.saveFile(multipartFile, true);
        File[] files = zipFileStorageManager.unzipFile(multipartFile.getOriginalFilename(), true);

        projectCollector.parseFiles(files);
	}

    /*
        This method generates JSON for class diagram generation in the front-end
     */
	public JSONArray getClassDiagramInfo() {
        Set<GraphElement> classes = projectCollector.getClasses();
        JSONArray jarray = new JSONArray();
        for (GraphElement ge: classes) {
            jarray.put(initClassJSON(ge));
        }
		return jarray;
    }

    private JSONObject initClassJSON(GraphElement ge) {
        JSONObject json = new JSONObject();
        json.put("ClassType", ge.getClassType());
        json.put("Name", ge.getClassName());
        json.put("TypeParameters", ge.getTypeParameters());
        json.put("Fields", classFieldsHelper(ge.getFields()));
        json.put("Methods", classMethodHelper(ge.getListOfMethods()));
        json.put("Relationship", classDepHelper(ge.getInh(), ge.getImp(), ge.getDep()));
        return json;
    }

    private JSONArray classFieldsHelper(Set<Field> fields) {
        JSONArray jarray = new JSONArray();
        for (Field f: fields) {
            JSONObject j = new JSONObject();
            j.put("FieldName", f.getName());
            j.put("Type", f.getType());
            j.put("Access", f.getAccess());
            jarray.put(j);
        }
        return jarray;
    }

    private JSONArray classMethodHelper(Set<Method> methods) {
        JSONArray jarray = new JSONArray();
        for (Method m: methods) {
            JSONObject j = new JSONObject();
            j.put("FuncName", m.getName());
            JSONArray ja = new JSONArray();
            for (String s: m.getParam()) {
                String[] temp = s.split(AnalysisConstants.PARAM_SEPARATOR);
                ja.put(temp[0]);
            }
            j.put("Param", ja);
            j.put("Access", m.getAccess());
            jarray.put(j);
        }
        return jarray;
    }

    private JSONObject classDepHelper(Set<String> depInh, Set<String> depImp, Set<String> dep) {
        JSONObject returnObj = new JSONObject();
        JSONArray inh = new JSONArray();
        for (String s0: depInh) {
            inh.put(s0);
        }
        returnObj.put("Inheritance", inh);
        JSONArray imp = new JSONArray();
        for (String s1: depImp) {
            imp.put(s1);
        }
        returnObj.put("Implementation", imp);
        JSONArray jdep = new JSONArray();
        for (String s1: dep) {
            jdep.put(s1);
        }
        returnObj.put("Dependency", jdep);
        return returnObj;
    }

	public JSONArray getSeqDiagramInfo(String className, String methodName) {
		return projectCollector.getSeqDiagramInfo(className, methodName);
	}
}