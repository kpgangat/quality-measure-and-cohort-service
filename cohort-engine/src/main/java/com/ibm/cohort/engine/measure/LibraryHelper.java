/*
 * (C) Copyright IBM Corp. 2020, 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.cohort.engine.measure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Measure;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.opencds.cqf.common.providers.LibraryResolutionProvider;
import org.opencds.cqf.common.providers.LibrarySourceProvider;

import com.ibm.cohort.translator.provider.InJVMCqlTranslationProvider;


/**
 * Helper functions for working with FHIR Library resources
 */
public class LibraryHelper {

	public static final String CODE_LOGIC_LIBRARY = "logic-library";
	public static final String CODE_SYSTEM_LIBRARY_TYPE = "http://terminology.hl7.org/CodeSystem/library-type";

	/**
	 * Create a LibraryLoader using the provided LibraryResolutionProvider.
	 * 
	 * @param provider Resource provider that handles loading the FHIR library
	 *                 resource that contains the CQL/ELM content.
	 * @return LibraryLoader that will base64 decode CQL text
	 */
	public static LibraryLoader createLibraryLoader(LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> provider) {
		InJVMCqlTranslationProvider translator = new InJVMCqlTranslationProvider();
		translator.addLibrarySourceProvider(
				new LibrarySourceProvider<org.hl7.fhir.r4.model.Library, org.hl7.fhir.r4.model.Attachment>(provider,
						x -> x.getContent(), x -> x.getContentType(), x -> x.getData()));

		return new LibraryLoader(provider, translator);
	}

	/**
	 * Load all of the libraries reference by a Measure resource and the Library
	 * resources is directly references. This will recurse through all of the
	 * library dependencies.
	 * 
	 * @param measure                 FHIR Measure resource
	 * @param libraryLoader           LibraryLoader library loading mechanism for
	 *                                handling CQL/ELM source to in memory library
	 *                                loading
	 * @param libraryResourceProvider Resource provider that handles loading the
	 *                                FHIR library resource that contains the
	 *                                CQL/ELM content
	 * @return List of executable logic libraries
	 */
	public static List<org.cqframework.cql.elm.execution.Library> loadLibraries(Measure measure,
			org.opencds.cqf.cql.engine.execution.LibraryLoader libraryLoader,
			LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider) {

		List<org.cqframework.cql.elm.execution.Library> libraries = new ArrayList<>();

		// TODO: The cqfmeasure IG says there should only be one library here. Should we
		// enforce that?
		// http://hl7.org/fhir/us/cqfmeasures/StructureDefinition-measure-cqfm.html
		for (CanonicalType ref : measure.getLibrary()) {
			org.hl7.fhir.r4.model.Library library = resolveLibrary(ref.getValue(), libraryLoader,
					libraryResourceProvider);
			if (library != null) {
				libraries.addAll(loadLibraries(library, libraryLoader, libraryResourceProvider));
			}
		}

		return libraries;
	}
	
	/**
	 * Load the provided Library and all of its dependencies using a recursive tree
	 * walk of related artifacts.
	 * 
	 * @param library                 FHIR Library resource
	 * @param libraryLoader           LibraryLoader library loading mechanism for
	 *                                handling CQL/ELM source to in memory library
	 *                                loading
	 * @param libraryResourceProvider Resource provider that handles loading the
	 *                                FHIR library resource that contains the
	 *                                CQL/ELM content
	 * @return List of executable logic libraries
	 */
	public static List<org.cqframework.cql.elm.execution.Library> loadLibraries(org.hl7.fhir.r4.model.Library library,
			org.opencds.cqf.cql.engine.execution.LibraryLoader libraryLoader,
			LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider) {
		Set<String> ids = new HashSet<>();
		return loadLibraries( library, libraryLoader, libraryResourceProvider, ids );
	}

	/**
	 * Load the provided Library and all of its dependencies using a recursive tree
	 * walk of related artifacts.
	 * 
	 * @param library                 FHIR Library resource
	 * @param libraryLoader           LibraryLoader library loading mechanism for
	 *                                handling CQL/ELM source to in memory library
	 *                                loading
	 * @param libraryResourceProvider Resource provider that handles loading the
	 *                                FHIR library resource that contains the
	 *                                CQL/ELM content
	 * @param loadedIds               Set of library IDs that have already been loaded by the tree walk. This is used for cycle detection.
	 * @return List of executable logic libraries
	 */
	protected static List<org.cqframework.cql.elm.execution.Library> loadLibraries(org.hl7.fhir.r4.model.Library library,
			org.opencds.cqf.cql.engine.execution.LibraryLoader libraryLoader,
			LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider, Set<String> loadedIds) {

		List<org.cqframework.cql.elm.execution.Library> libraries = new ArrayList<>();
		
		if( ! loadedIds.contains(library.getId()) ) {

			loadedIds.add( library.getId() );
			
			VersionedIdentifier libraryIdentifier = new VersionedIdentifier().withId(library.getName())
					.withVersion(library.getVersion());
			org.cqframework.cql.elm.execution.Library executable = libraryLoader.load(libraryIdentifier);
			libraries.add(executable);
	
			for (RelatedArtifact related : library.getRelatedArtifact()) {
				if (related.hasType() && related.getType().equals(RelatedArtifact.RelatedArtifactType.DEPENDSON)
						&& related.hasResource()) {
					org.hl7.fhir.r4.model.Library child = resolveLibrary(related.getResource(), libraryLoader,
							libraryResourceProvider);
					if (child != null) {
						libraries.addAll(loadLibraries(child, libraryLoader, libraryResourceProvider, loadedIds));
					}
				}
			}
		}

		return libraries;
	}

	/**
	 * Resolve a FHIR Library resource based on the string reference provided.
	 * 
	 * @param resource                String reference to a FHIR Library resource
	 * @param libraryLoader           LibraryLoader library loading mechanism for
	 *                                handling CQL/ELM source to in memory library
	 *                                loading
	 * @param libraryResourceProvider Resource provider that handles loading the
	 *                                FHIR library resource that contains the
	 *                                CQL/ELM content
	 * @return Loaded FHIR Library resource or null if the String does not contain
	 *         an understood reference
	 */
	public static org.hl7.fhir.r4.model.Library resolveLibrary(String resource,
			org.opencds.cqf.cql.engine.execution.LibraryLoader libraryLoader,
			LibraryResolutionProvider<org.hl7.fhir.r4.model.Library> libraryResourceProvider) {

		org.hl7.fhir.r4.model.Library library = null;

		// Raw references to Library/libraryId or libraryId
		if (resource.startsWith("Library/") || !resource.contains("/")) {
			library = libraryResourceProvider.resolveLibraryById(resource.replace("Library/", ""));
		}
		// Full url (e.g. http://hl7.org/fhir/us/Library/FHIRHelpers)
		else if (resource.contains(("/Library/"))) {
			library = libraryResourceProvider.resolveLibraryByCanonicalUrl(resource);
		}

		if( isLogicLibrary(library) ) {
			return library;
		} else { 
			return null;
		}
	}
	
	/**
	 * Perform basic checks to verify that the loaded Library contains CQL/ELM logic that 
	 * can be evaluated by the runtime engine. This logic is cribbed from the cqf-ruler
	 * LibraryHelper implementation. It is specificaly useful for Library content related
	 * to the measure, such as the FHIR modelinfo file, that is not directly part of the 
	 * CQL logic.
	 * 
	 * @param library FHIR Library resource
	 * @return true if the Library resource contains executable CQL logic or false otherwise
	 */
	public static boolean isLogicLibrary(org.hl7.fhir.r4.model.Library library) {
        if (library == null) {
            return false;
        }

        if (!library.hasType()) {
            // If no type is specified, assume it is a logic library based on whether there is a CQL content element.
            if (library.hasContent()) {
                for (org.hl7.fhir.r4.model.Attachment a : library.getContent()) {
                    if (a.hasContentType() && (a.getContentType().equals("text/cql")
                            || a.getContentType().equals("application/elm+xml")
                            || a.getContentType().equals("application/elm+json"))) {
                        return true;
                    }
                }
            }
            return false;
        }

        if (!library.getType().hasCoding()) {
            return false;
        }

        for (Coding c : library.getType().getCoding()) {
            if (c.hasSystem() && c.getSystem().equals(CODE_SYSTEM_LIBRARY_TYPE)
                    && c.hasCode() && c.getCode().equals(CODE_LOGIC_LIBRARY)) {
                return true;
            }
        }

        return false;
    }
}
