package com.program.analysis.app;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import com.program.analysis.app.representation.ProjectParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.json.JSONArray;

@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:4200" })
@RestController
public class DiagramProviderController {

	@Autowired
	private DiagramProviderService diagramProviderService;

 	@GetMapping("/analysis/health")
 	public ResponseEntity<String> statusCheck() {
 		return ResponseEntity.status(HttpStatus.OK).body("OK");
 	}

	@PutMapping("/analysis/upload")
	public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {

		try {
			diagramProviderService.upload(file);
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File exception:" + e.getLocalizedMessage());
		} catch (ProjectParseException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getLocalizedMessage());
		}

		return ResponseEntity.status(HttpStatus.OK).body("Upload successful");
	}

	@GetMapping("/analysis/class/info")
	public ResponseEntity<String> getClassInfos() {
		JSONArray classInfos = diagramProviderService.getClassDiagramInfo();

		return new ResponseEntity<String>(classInfos.toString(), HttpStatus.OK);
	}

	@GetMapping("/analysis/class/{name}/sequence/{method}")
	public ResponseEntity<String> getSequenceInfos(@PathVariable("name") String className, @PathVariable("method") String methodName) {
		JSONArray sequenceInfos = diagramProviderService.getSeqDiagramInfo(className, methodName);
		
		if (sequenceInfos == null) {
			return new ResponseEntity<String>("", HttpStatus.OK);
		}

		return new ResponseEntity<String>(sequenceInfos.toString(), HttpStatus.OK);
	}
}
