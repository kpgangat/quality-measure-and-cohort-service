/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.Writer;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.RelatedArtifact.RelatedArtifactType;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cohort.engine.BasePatientTest;
import com.ibm.cohort.fhir.client.config.FhirServerConfig;

import ca.uhn.fhir.parser.IParser;

public class CohortCLITest extends BasePatientTest {
	@Test
	public void testMainWithParams() throws Exception {
		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, null);

		FhirServerConfig fhirConfig = getFhirServerConfig();

		setupTestFor(patient, fhirConfig, "cql/basic/test.xml");

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/parameters", "-l", "test-with", "-v", "params", "-e", "Female", "-e", "Male", "-c",
						"123", "-p", "MaxAge:integer:40" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");
			assertEquals(5, lines.length);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}

	@Test
	public void testMainNoParams() throws Exception {
		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, null);

		FhirServerConfig fhirConfig = getFhirServerConfig();
		setupTestFor(patient, fhirConfig, "cql/basic/test.xml");

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/basic", "-l", "test", "-e", "Female", "-e", "Male", "-e",
						"Over the hill", "-c", "123" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");
			assertEquals(6, lines.length);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}
	
	@Test
	public void testMainMultiFolder() throws Exception {
		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, null);

		FhirServerConfig fhirConfig = getFhirServerConfig();
		setupTestFor(patient, fhirConfig, "cql/basic/test.xml");

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/multi-folder", "-l", "Cohort1", "-v", "1.0.0", "-c", "123" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");
			assertEquals( String.join("\n", lines), 5, lines.length);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}

	@Test
	public void testMainZippedLibraries() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		mockFhirResourceRetrieval("/metadata?_format=json", getCapabilityStatement());

		Patient justRight = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		mockFhirResourceRetrieval(justRight);

		Patient tooOld = getPatient("456", Enumerations.AdministrativeGender.FEMALE, "1900-08-01");
		mockFhirResourceRetrieval(tooOld);

		Patient tooManly = getPatient("789", Enumerations.AdministrativeGender.MALE, "1978-05-06");
		mockFhirResourceRetrieval(tooManly);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/zip/breast_cancer_screening_v1_0_0_cql.zip", "-l",
						"Breast-Cancer", "-v", "Screening", "-e", "Female", "-e", "40-65 years of age", "-e",
						"MeetsInclusionCriteria", "-c", "123", "-c", "456", "-c", "789" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");

			assertEquals(output, 16, lines.length);
			System.out.println(output);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Patient/456?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Patient/789?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}

	@Test
	public void testMainZippedLibrariesWithCompilation() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		IParser encoder = getFhirParser();

		mockFhirResourceRetrieval("/metadata?_format=json", encoder, getCapabilityStatement(), fhirConfig);

		Patient justRight = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		mockFhirResourceRetrieval(justRight);

		Patient tooOld = getPatient("456", Enumerations.AdministrativeGender.FEMALE, "1900-08-01");
		mockFhirResourceRetrieval(tooOld);

		Patient tooManly = getPatient("789", Enumerations.AdministrativeGender.MALE, "1978-05-06");
		mockFhirResourceRetrieval(tooManly);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/zip/breast_cancer_screening_v1_0_0_cql.zip", "-l",
						"Breast-Cancer", "-v", "Screening", "-e", "Female", "-e", "40-65 years of age", "-e",
						"MeetsInclusionCriteria", "-c", "123", "-c", "456", "-c", "789", "-s", "CQL" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");

			assertEquals(output, 16, lines.length);
			System.out.println(output);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Patient/456?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Patient/789?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}
	
	@Test
	public void testMainZippedLibrariesMultiFolderWithExtraEntries() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		IParser encoder = getFhirParser();

		mockFhirResourceRetrieval("/metadata?_format=json", encoder, getCapabilityStatement(), fhirConfig);

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		mockFhirResourceRetrieval(patient);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/zip-with-folders/cohorts.zip", "-l",
						"Breast-Cancer", "-v", "Screening", "-e", "Female", "-e", "Ages 40 to 75", "-e",
						"MeetsInclusionCriteria", "-c", "123", "-s", "CQL" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");

			assertEquals(output, 6, lines.length);
			System.out.println(output);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}	
	
	@Test
	public void testMainZippedLibrariesMultiFolderWithExtraEntriesFiltered() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		IParser encoder = getFhirParser();

		mockFhirResourceRetrieval("/metadata?_format=json", encoder, getCapabilityStatement(), fhirConfig);

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, 65);
		mockFhirResourceRetrieval(patient);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/zip-with-folders/cohorts.zip", "--filter", "cohorts/ci_colorectal_cancer_screening_v1_0_0_cql", "-l",
						"CI_Colorectal-Cancer", "-v", "Screening", "-e", "Individuals 50-75 years of age", "-c", "123", "-s", "CQL" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");

			assertEquals(output, 4, lines.length);
			System.out.println(output);

			verify(1, getRequestedFor(urlEqualTo("/Patient/123?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testMainZippedLibrariesMultiFolderWithExtraEntriesFilteredNoneFound() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		IParser encoder = getFhirParser();

		mockFhirResourceRetrieval("/metadata?_format=json", encoder, getCapabilityStatement(), fhirConfig);

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, 65);
		mockFhirResourceRetrieval(patient);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-t", tmpFile.getAbsolutePath(),
						"-f", "src/test/resources/cql/zip-with-folders/cohorts.zip", "--filter", "does-not-exist", "-l",
						"CI_Colorectal-Cancer", "-v", "Screening", "-e", "Individuals 50-75 years of age", "-c", "123", "-s", "CQL" });
				
				fail("Execution should not reach this point");
			} finally {
				System.setOut(originalOut);
			}

		} finally {
			tmpFile.delete();
		}
	}

	@Test
	public void testMainFHIRLibrariesWithDependencies() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		mockFhirResourceRetrieval("/metadata?_format=json", getCapabilityStatement());

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		mockFhirResourceRetrieval(patient);

		Library root = getLibrary("Breast-Cancer-Screening", DEFAULT_RESOURCE_VERSION, "cql/includes/Breast-Cancer-Screening.cql");
		Library helpers = getLibrary("FHIRHelpers", "4.0.0", "cql/fhir-helpers/FHIRHelpers.cql", "text/cql",
				"cql/fhir-helpers/FHIRHelpers.xml", "application/elm+json");

		RelatedArtifact related = new RelatedArtifact();
		related.setType(RelatedArtifactType.DEPENDSON);
		related.setResource("/Library/" + helpers.getId());
		root.addRelatedArtifact(related);

		mockFhirResourceRetrieval(root);
		mockFhirSingletonBundleRetrieval(helpers);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-f", root.getId(), "-l",
						root.getName(), "-v", root.getVersion(), "-c", patient.getId(), "-s", "CQL" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			String[] lines = output.split("\r?\n");

			assertEquals(output, 12, lines.length);
			System.out.println(output);

			verify(1, getRequestedFor(urlEqualTo("/Patient/" + patient.getId() + "?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Library/" + root.getId() + "?_format=json")));
		} finally {
			tmpFile.delete();
		}
	}
	
	@Test
	public void testMainMultipleResultTypes() throws Exception {

		FhirServerConfig fhirConfig = getFhirServerConfig();

		mockFhirResourceRetrieval("/metadata?_format=json", getCapabilityStatement());

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		patient.setMaritalStatus(new CodeableConcept(new Coding("http://hl7.org/fhir/ValueSet/marital-status", "M", "Married")));
		mockFhirResourceRetrieval(patient);

		Condition condition = new Condition();
		condition.setSubject(new Reference(patient.getId()));
		condition.setCode(new CodeableConcept(new Coding("http://snomed.com/snomed/2020", "1234", "Dummy")));
		mockFhirResourceRetrieval("/Condition?code%3Ain=http%3A%2F%2Fsome.io%2Fcondition&subject=Patient%2F" + patient.getId() + "&_count=500&_format=json", condition);
		
		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-f", "src/test/resources/cql/result-types", "-l",
						"test_result_types", "-c", patient.getId(), "--enable-terminology-optimization", "--search-page-size", "500" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			assertTrue( output.contains( "Collection: 1") );
			assertTrue( output.contains( "Patient/123") );
			assertTrue( output.contains( "false") );
			assertTrue( output.contains( "DateType[1978-05-06]") );
			assertTrue( output.contains( "Enumeration[female]") );
			
			String[] lines = output.split("\r?\n");
			assertEquals(output, 9, lines.length);
			System.out.println(output);
		} finally {
			tmpFile.delete();
		}
	}
	
	@Test
	public void testCQLTranslationCustomIGWithTargetUrl()  throws Exception{
		FhirServerConfig fhirConfig = getFhirServerConfig();

		mockFhirResourceRetrieval("/metadata?_format=json", getCapabilityStatement());

		Patient patient = getPatient("123", Enumerations.AdministrativeGender.FEMALE, "1978-05-06");
		patient.addExtension(new Extension("http://fakeIg.com/fake-extension", new StringType("fakeValue")));
		mockFhirResourceRetrieval(patient);

		Library root = getLibrary("test", DEFAULT_RESOURCE_VERSION, "cql/ig-test/test.cql");
		Library helpers = getLibrary("FHIRHelpers", "4.0.0", "cql/fhir-helpers/FHIRHelpers.cql", "text/cql",
				"cql/fhir-helpers/FHIRHelpers.xml", "application/elm+json");

		RelatedArtifact related = new RelatedArtifact();
		related.setType(RelatedArtifactType.DEPENDSON);
		related.setResource("/Library/" + helpers.getId());
		root.addRelatedArtifact(related);

		mockFhirResourceRetrieval(root);
		mockFhirSingletonBundleRetrieval(helpers);

		File tmpFile = new File("target/fhir-stub.json");
		ObjectMapper om = new ObjectMapper();
		try (Writer w = new FileWriter(tmpFile)) {
			w.write(om.writeValueAsString(fhirConfig));
		}

		try {
			PrintStream originalOut = System.out;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream captureOut = new PrintStream(baos)) {
				System.setOut(captureOut);
				CohortCLI.main(new String[] { "-d", tmpFile.getAbsolutePath(), "-f", root.getId(), "-l",
						root.getName(), "-v", root.getVersion(), "-c", patient.getId(), "-s", "CQL", "-i", "src/test/resources/modelinfo/ig-with-target-modelinfo-0.0.1.xml" });
			} finally {
				System.setOut(originalOut);
			}

			String output = new String(baos.toByteArray());
			System.out.println(output);

			verify(2, getRequestedFor(urlEqualTo("/Patient/" + patient.getId() + "?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Library/" + root.getId() + "?_format=json")));
			verify(1, getRequestedFor(urlEqualTo("/Library?url=%2FLibrary%2F" + helpers.getId() + "&_format=json")));
		} finally {
			tmpFile.delete();
		}
	}
}
