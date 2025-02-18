/*
 * (C) Copyright IBM Corp. 2021, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine;

import java.util.Arrays;

import org.apache.commons.io.FilenameUtils;
import org.hl7.elm.r1.VersionedIdentifier;
import org.hl7.fhir.r4.model.Patient;
import org.opencds.cqf.cql.engine.execution.LibraryLoader;

import com.ibm.cohort.fhir.client.config.FhirServerConfig;
import com.ibm.cohort.fhir.client.config.IBMFhirServerConfig;
import com.ibm.cohort.translator.provider.CqlTranslationProvider;
import com.ibm.cohort.translator.provider.InJVMCqlTranslationProvider;
import com.ibm.cohort.version.DefaultFilenameToVersionedIdentifierStrategy;
import com.ibm.cohort.version.FilenameToVersionedIdentifierStrategy;

public class BasePatientTest extends BaseFhirTest {
	protected CqlEvaluator setupTestFor(Patient patient, String... resources) {
		IBMFhirServerConfig fhirConfig = new IBMFhirServerConfig();
		fhirConfig.setEndpoint("http://localhost:" + HTTP_PORT);
		fhirConfig.setUser("fhiruser");
		fhirConfig.setPassword("change-password");
		fhirConfig.setTenantId("default");

		return setupTestFor(patient, fhirConfig, resources);
	}

	protected CqlEvaluator setupTestFor(Patient patient, FhirServerConfig fhirConfig, String... resources) {

		mockFhirResourceRetrieval("/metadata?_format=json", getCapabilityStatement());
		mockFhirResourceRetrieval(patient);

		CqlEvaluator wrapper = new CqlEvaluator();
		if (resources != null) {
			/*
			 * Do some hacking to make the pre-existing test resources still function 
			 * with the updated design.
			 */
			FilenameToVersionedIdentifierStrategy strategy = new DefaultFilenameToVersionedIdentifierStrategy() {
				@Override
				public VersionedIdentifier filenameToVersionedIdentifier(String filename) {
					VersionedIdentifier result = null;
					String basename = FilenameUtils.getBaseName(filename);
					if( basename.startsWith("test") ) {
						result = new VersionedIdentifier().withId("Test").withVersion("1.0.0");
					} else { 
						result = super.filenameToVersionedIdentifier( filename );
					}
					return result;
				}
			};
			
			MultiFormatLibrarySourceProvider sourceProvider = new TestClasspathLibrarySourceProvider(
					Arrays.asList(resources),
					strategy);
			CqlTranslationProvider translationProvider = new InJVMCqlTranslationProvider(sourceProvider);

			LibraryLoader libraryLoader = new TranslatingLibraryLoader(sourceProvider, translationProvider);
			wrapper.setLibraryLoader(libraryLoader);
		}

		wrapper.setDataServerConnectionProperties(fhirConfig);
		wrapper.setTerminologyServerConnectionProperties(fhirConfig);
		wrapper.setMeasureServerConnectionProperties(fhirConfig);
		// This is a hack to get all the old tests that were written
		// assuming server-default page sizes to continue to work.
		wrapper.setSearchPageSize(null);
		return wrapper;
	}
}
